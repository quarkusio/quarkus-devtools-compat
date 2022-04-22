package io.quarkus.devtools.compat;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.io.IOException;

public class GithubIssueCreateOnFailureListener implements TestExecutionListener {

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {

        if (testExecutionResult.getStatus().equals(TestExecutionResult.Status.FAILED)) {
            final String githubToken = System.getenv("GITHUB_TOKEN");
            if(githubToken == null) {
                return;
            }
            try {
                GitHub github = new GitHubBuilder().withOAuthToken(githubToken).build();
                final GHRepository repo = github.getRepository(System.getenv("REPOSITORY_NAME"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
