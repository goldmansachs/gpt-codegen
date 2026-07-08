package org.rj.modelgen.llm.subproblem.data;

// Holds details on a subproblem to be recombined. subproblemId is the original id
// from the decomposition step — needed because the list passed to combineSubproblems()
// may have gaps when some subproblems failed and were skipped.
public record SubproblemDetails(int subproblemId, String request, String result) { }
