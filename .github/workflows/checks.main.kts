#!/usr/bin/env kotlin

@file:DependsOn("io.github.typesafegithub:github-workflows-kt:1.9.0")
@file:Suppress("PropertyName")

import io.github.typesafegithub.workflows.actions.actions.CheckoutV4
import io.github.typesafegithub.workflows.actions.actions.SetupJavaV4
import io.github.typesafegithub.workflows.actions.gradle.GradleBuildActionV2
import io.github.typesafegithub.workflows.actions.gradle.WrapperValidationActionV1
import io.github.typesafegithub.workflows.domain.AbstractResult
import io.github.typesafegithub.workflows.domain.Concurrency
import io.github.typesafegithub.workflows.domain.JobOutputs
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.PullRequest
import io.github.typesafegithub.workflows.domain.triggers.Push
import io.github.typesafegithub.workflows.dsl.expressions.expr
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.writeToFile

workflow(
    name = "Compilation Checks",
    on = listOf(
        Push(branchesIgnore = listOf("repo"), pathsIgnore = listOf("**.md")),
        PullRequest(
            branchesIgnore = listOf("repo"),
            pathsIgnore = listOf("**.md"),
        )
    ),
    sourceFile = __FILE__.toPath(),
    concurrency = Concurrency(group = expr { github.workflow }, cancelInProgress = true),
    env = linkedMapOf(),
) {

    job(
        id = "template_check",
        name = "Check for libs and multisrc compilation",
        runsOn = UbuntuLatest,
        env = linkedMapOf(),
        outputs = object : JobOutputs() {},
    ) {
        uses(name = "Clone repo", action = CheckoutV4())
        uses(name = "Validate Gradle Wrapper", action = WrapperValidationActionV1())
        uses(name = "Set up JDK", action = SetupJavaV4(javaVersion = "21", distribution = SetupJavaV4.Distribution.Adopt))
        val setupGradle = uses(name = "Setup Gradle", action = GradleBuildActionV2())

        run(name = "Check libs", `if` = expr { setupGradle.outcome.eq(AbstractResult.Status.Success) }, command = "./gradlew :compileLibsKotlin")
        run(name = "Check multisrc", `if` = expr { setupGradle.outcome.eq(AbstractResult.Status.Success) }, command = "./gradlew :compileMultiSrcKotlin")
    }

}.writeToFile()
