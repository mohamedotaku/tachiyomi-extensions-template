#!/usr/bin/env kotlin

@file:DependsOn("io.github.typesafegithub:github-workflows-kt:1.9.0")
@file:Suppress("PropertyName")

import io.github.typesafegithub.workflows.actions.actions.CheckoutV4
import io.github.typesafegithub.workflows.actions.actions.DownloadArtifactV4
import io.github.typesafegithub.workflows.actions.actions.SetupJavaV4
import io.github.typesafegithub.workflows.actions.actions.UploadArtifactV4
import io.github.typesafegithub.workflows.actions.gradle.GradleBuildActionV2
import io.github.typesafegithub.workflows.actions.gradle.WrapperValidationActionV1
import io.github.typesafegithub.workflows.domain.Concurrency
import io.github.typesafegithub.workflows.domain.JobOutputs
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.actions.Action
import io.github.typesafegithub.workflows.domain.actions.RegularAction
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.expressions.expr
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.writeToFile

workflow(
    name = "Construct Release",
    on = listOf(WorkflowDispatch()/*, Push(branchesIgnore = listOf("repo"), pathsIgnore = listOf("**.md"))*/),
    sourceFile = __FILE__.toPath(),
    concurrency = Concurrency(group = expr { github.workflow }, cancelInProgress = true),
    env = linkedMapOf(),
) {

    val assembleRelease = job(
        id = "assemble_release",
        name = "Construct Release extensions repository",
        runsOn = UbuntuLatest,
        env = linkedMapOf(),
        outputs = object : JobOutputs() {},
    ) {
        uses(name = "Clone repo", action = CheckoutV4())
        uses(name = "Validate Gradle Wrapper", action = WrapperValidationActionV1())
        uses(name = "Set up JDK", action = SetupJavaV4(javaVersion = "21", distribution = SetupJavaV4.Distribution.Adopt))
        uses(name = "Setup Gradle", action = GradleBuildActionV2())

        run(name = "Prepare signing key", command = "echo ${expr { secrets["KEY_STORE"]!! }} | base64 -d > ${expr { secrets["KEY_FILE_NAME"]!! }}")
        run(
            name = "Construct release Repo",
            command = "./gradlew :constructReleaseRepo",
            env = linkedMapOf(
                "KEY_FILE_NAME" to expr { secrets["KEY_FILE_NAME"]!! },
                "KEY_STORE_PASSWORD" to expr { secrets["KEY_STORE_PASSWORD"]!! },
                "KEY_STORE_ALIAS" to expr { secrets["KEY_STORE_ALIAS"]!! },
                "KEY_PASSWORD" to expr { secrets["KEY_PASSWORD"]!! },
            )
        )

        uses(
            name = "Upload repo", action = UploadArtifactV4(
                name = "release-repo",
                path = listOf("./build/repo/release/"),
                ifNoFilesFound = UploadArtifactV4.BehaviorIfNoFilesFound.Error,
                retentionDays = UploadArtifactV4.RetentionPeriod.Value(1)
            )
        )

        run(name = "Clean up CI files", `if` = expr { always() }, command = "rm ${expr { secrets["KEY_FILE_NAME"]!! }}")
    }

    val publishRepo = job(
        id = "publish_repo",
        name = "Publish release repo",
        runsOn = UbuntuLatest,
        env = linkedMapOf(),
        needs = listOf(assembleRelease),
        permissions = mapOf(Permission.Contents to Mode.Write),
        `if` = """'true' == ${expr { "vars.DO_PUBLISH_REPO" }}""",
        outputs = object : JobOutputs() {},
    ) {

        uses(name = "Checkout repo branch", action = CheckoutV4(ref = "repo", path = "repo"))
        uses(name = "Download updated release", action = DownloadArtifactV4(name = "release-repo", path = "~/release"))

        run(name = "Fail on error", command = "set -e")
        run(name = "Show release contents", command = "ls -AR ~/release")
        run(name = "Show repo contents", command = "ls -AR ./repo")

        run(name = "Delete old contents", command = buildString {
            appendLine("cd ./repo")
            appendLine("shopt -s extglob")
            appendLine("rm -rf !(.git)")
            appendLine("shopt -u extglob")
            appendLine("ls -AR .")
        })
        run(name = "Copy new contents", command = "cp -aT ~/release/ ./repo")
        run(name = "Set email and name", command = buildString {
            appendLine("git config --global user.email \"github-actions[bot]@users.noreply.github.com\"")
            appendLine("git config --global user.name \"github-actions[bot]\"")
        })
        run(name = "Commit if necessary", command = buildString {
            appendLine("cd ./repo")
            appendLine("ls -AR .")
            appendLine("git status")
            appendLine("if [ -n \"\$(git status --porcelain)\" ]; then")
            appendLine("    git add .")
            appendLine("    git commit -m \"Update repo\"")
            appendLine("    git push")
            appendLine("    echo \"Repository updated\"")
            appendLine("else")
            appendLine("    echo \"No changes to commit\"")
            appendLine("fi")
        })

        class PurgeJsDelivrCacheV1(private val url: List<String>, private val attempts: Int? = null) : RegularAction<Action.Outputs>("gacts", "purge-jsdelivr-cache", "v1") {
            override fun toYamlArguments(): LinkedHashMap<String, String> = LinkedHashMap<String, String>().apply {
                put("url", url.joinToString("\n"))
                attempts?.let { put("attempts", attempts.toString()) }
            }

            override fun buildOutputObject(stepId: String): Outputs = Outputs(stepId)
        }

        // jsDelivr url should be of the form: https://cdn.jsdelivr.net/gh/{username}/{repo}@{branch}/{path/to/file}
        uses(
            name = "Purge JsDelivr Cache",
            action = PurgeJsDelivrCacheV1(
                url = listOf(
                    """https://cdn.jsdelivr.net/gh/${expr { github.repository }}@repo/index.json""",
                    """https://cdn.jsdelivr.net/gh/${expr { github.repository }}@repo/index.min.json""",
                ),
                attempts = 3,
            ),
        )
    }

}.writeToFile()
