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
import io.github.typesafegithub.workflows.domain.triggers.Release
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.expressions.expr
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.writeToFile

workflow(
    name = "Strip release",
    on = listOf(WorkflowDispatch(), Release()),
    sourceFile = __FILE__.toPath(),
    concurrency = Concurrency(group = expr { github.workflow }, cancelInProgress = true),
    env = linkedMapOf(),
) {

    val strip = job(
        id = "strip_template",
        name = "Strips the template",
        runsOn = UbuntuLatest,
        `if` = """742158806 == ${expr { github.repository_id }}""",
        env = linkedMapOf(),
        outputs = object : JobOutputs() {},
    ) {
        uses(name = "Clone repo", action = CheckoutV4())
        uses(name = "Validate Gradle Wrapper", action = WrapperValidationActionV1())
        uses(name = "Set up JDK", action = SetupJavaV4(javaVersion = "21", distribution = SetupJavaV4.Distribution.Adopt))
        uses(name = "Setup Gradle", action = GradleBuildActionV2())

        run(name = "Strip template", command = "./gradlew :strip:template")
        uses(
            name = "Upload strip.zip",
            action = UploadArtifactV4(
                name = "stripped-template",
                path = listOf("./build/stripped-template.zip"),
                ifNoFilesFound = UploadArtifactV4.BehaviorIfNoFilesFound.Error,
                retentionDays = UploadArtifactV4.RetentionPeriod.Value(1)
            )
        )
    }

    job(
        id = "edit_release",
        name = "Add stripped template to release",
        runsOn = UbuntuLatest,
        `if` = """742158806 == ${expr { github.repository_id }}""",
        needs = listOf(strip),
        env = linkedMapOf(),
        permissions = mapOf(Permission.Contents to Mode.Write),
        outputs = object : JobOutputs() {},
    ) {
        uses(name = "Download stripped template", action = DownloadArtifactV4(name = "stripped-template", path = "."))

        class EditReleaseV1(
            private val token: String = expr { secrets.GITHUB_TOKEN },
            private val id: String = expr { github.eventRelease.release.id },
            private val files: List<String>? = null
        ) : RegularAction<Action.Outputs>("irongut", "EditRelease", "v1.2.0") {
            override fun toYamlArguments(): LinkedHashMap<String, String> = LinkedHashMap<String, String>().apply {
                put("token", token)
                put("id", id)
                files?.let { put("files", it.joinToString(",")) }
            }

            override fun buildOutputObject(stepId: String): Outputs = Outputs(stepId)
        }

        uses(
            name = "Edit release",
            action = EditReleaseV1(
                files = listOf("./stripped-template.zip")
            ),
        )
    }

}.writeToFile()
