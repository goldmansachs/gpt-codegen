package org.rj.modelgen.llm.subproblem.data;

public class SubproblemDecomposition {
    public static String getSubproblemToSubprocessDataKey(int subproblemId) {
        return "subproblem-%d-subprocess-name".formatted(subproblemId);
    }

    public static String getSubprocessNameToSubproblemDataKey(String subprocessName) {
        return "subprocess-subproblem-id-%s".formatted(subprocessName);
    }
}
