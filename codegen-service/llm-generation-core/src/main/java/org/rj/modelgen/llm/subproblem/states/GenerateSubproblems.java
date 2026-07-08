package org.rj.modelgen.llm.subproblem.states;

import org.apache.commons.lang3.StringUtils;
import org.rj.modelgen.llm.state.ModelInterfaceSignal;
import org.rj.modelgen.llm.statemodel.data.common.StandardModelData;
import org.rj.modelgen.llm.subproblem.data.SubproblemDecompositionPayloadData;
import org.rj.modelgen.llm.subproblem.data.SubproblemDecomposition;
import org.rj.modelgen.llm.util.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public abstract class GenerateSubproblems extends SubproblemDecompositionBaseState {
    private static final Pattern BEGIN_SECTION_MATCHER = Pattern.compile("\\(BEGIN(?:\\s+([A-Za-z0-9]+))?(?=[^A-Za-z0-9]|$)[^\\r\\n]*\\R?", Pattern.CASE_INSENSITIVE);

    private static final Logger LOG = LoggerFactory.getLogger(GenerateSubproblems.class);
    private String inputKey = StandardModelData.Request.toString();
    private String outputKey = StandardModelData.Request.toString();

    private record SubprocessRecord(String name, String content) { }

    public GenerateSubproblems() {
        this(GenerateSubproblems.class);
    }

    public GenerateSubproblems(Class<? extends GenerateSubproblems> cls) {
        super(cls);
    }

    @Override
    protected Mono<ModelInterfaceSignal> execute(ModelInterfaceSignal inputSignal) {
        // If problem decomposition is disabled we can just continue, since the full problem will already
        // be present in `request` for the rest of the model to work on
        if (!shouldDecomposeIntoSubproblems()) {
            LOG.info("Subproblem decomposition is disabled, continuing with full problem");
            return success("Subproblem decomposition is not enabled");
        }

        // If no subproblem ID is present then we have not yet run this process, and should decompose into subproblems now
        if (!getPayload().hasData(SubproblemDecompositionPayloadData.CurrentSubproblem)) {
            final var decompResult = triggerSubproblemDecomposition();
            if (decompResult.isErr()) return error(decompResult.getError());
        }

        // We have at least one subproblem to execute
        final int lastSubproblemId = getCurrentSubproblemId();
        final int subproblemCount = getSubproblemCount();

        if (lastSubproblemId >= (subproblemCount - 1)) {
            return error(String.format(
                    "Invalid subproblem state; trying to increment from previous subproblem %d with total subproblem count %d", lastSubproblemId, subproblemCount));
        }

        // Move to the next subproblem
        final var subproblemId = (lastSubproblemId + 1);
        getPayload().put(SubproblemDecompositionPayloadData.CurrentSubproblem, subproblemId);
        LOG.info("Starting processing of subproblem ID {} ({} of {})", subproblemId, subproblemId + 1, subproblemCount);

        // Get the request content for subproblem N and make it the active request which the rest of the model will work on
        final var requestKey = subproblemRequestContentKey(subproblemId);
        final String requestContent = getPayload().getOrThrow(requestKey, () -> new RuntimeException(
                String.format("No subproblem request content for subproblem %d (at %s)", subproblemId, requestKey)));

        getPayload().put(outputKey, requestContent);

        // Allow subclasses to perform their own preparation before starting work
        onStartingNewSubproblem(subproblemId, subproblemCount);

        return success("Subproblem generation successful");
    }

    private Result<Void, String> triggerSubproblemDecomposition() {
        final String problem = getPayload().getOrThrow(inputKey, () -> new RuntimeException("No problem data available for decomposition"));

        final var result = decomposeIntoSubproblems(problem);
        if (result.isErr()) return Result.Err(result.getError());

        // Store problem decomposition into payload for each iteration
        final List<String> subproblems = result.getValue();
        for (int i = 0; i < subproblems.size(); ++i) {
            getPayload().put(subproblemRequestContentKey(i), subproblems.get(i));
        }

        // Set 'last' subproblem to -1 so that the first execution increments to subproblem 0
        setCurrentSubproblemId(-1);
        setSubproblemCount(subproblems.size());

        LOG.info("Subproblem decomposition complete; generated {} subproblems", subproblems.size());
        return Result.Ok();
    }

    /**
     * Perform subproblem decomposition.  Returns a list containing the initial request content for each subproblem
     * @param problem       Problem to be decomposed and returned
     */
    protected Result<List<String>, String> decomposeIntoSubproblems(String problem) {
        String mainProcess = null;
        final List<SubprocessRecord> subProcesses = new ArrayList<>();

        // Identify all problem/subproblem blocks and extract them
        final var matcher = BEGIN_SECTION_MATCHER.matcher(problem);
        while (matcher.find()) {
            final var subprocessName = matcher.group(1);
            final var startIndex = matcher.end();
            final var endTag = endTagPattern(subprocessName);
            final var endMatch = endTag.matcher(problem);
            endMatch.region(startIndex, problem.length());
            final var endIndex = endMatch.find() ? endMatch.start() : -1;
            if (startIndex < 0 || endIndex < 0 || (endIndex - 1) <= startIndex) {
                LOG.error("Error while finding subset of content for (sub)process block '{}'", matcher.group(0));
                continue;
            }
            final var content = problem.substring(startIndex, endIndex - 1).strip();
            if (subprocessName == null) {
                if (mainProcess != null) LOG.warn("Multiple main process blocks found; overwriting previous one");
                mainProcess = content;
            } else {
                subProcesses.add(new SubprocessRecord(subprocessName, content));
            }
            LOG.debug("Identified {}process block{}",
                    (subprocessName == null ? "main " : "sub"),
                    (subprocessName == null ? "" : " '" + subprocessName + "'"));
        }

        if (mainProcess == null) {
            LOG.error("No main process block found when decomposing problem into sub-problems.  Using full problem as fallback");
            mainProcess = problem;
        }

        LOG.info("Decomposed problem into one main process and {} sub-process{}", subProcesses.size(), subProcesses.size() == 1 ? "" : "es");
        for (int ix = 0; ix < subProcesses.size(); ++ix) {
            writeSubproblemLocatorsToPayload(ix + 1, subProcesses.get(ix));
        }

        final var subProblems = Stream.concat(Stream.of(mainProcess), subProcesses.stream().map(SubprocessRecord::content)).toList();
        return Result.Ok(subProblems);
    }

    /**
     * Triggered when we are about to prepare a new subproblem.  Can be overridden by subclasses to e.g. perform
     * cleanup of iteration N data in preparation for iteration N+1
     *
     * @param subproblemId          ID of the problem we are about to begin, from 0 to N-1
     * @param subproblemCount       Total number of subproblems (0 <= subproblemId < subproblemCount)
     */
    protected void onStartingNewSubproblem(int subproblemId, int subproblemCount) { }

    public GenerateSubproblems withInputKey(String inputKey) {
        this.inputKey = inputKey;
        return this;
    }

    public<T extends Enum<?>> GenerateSubproblems withInputKey(T inputKey) {
        return withInputKey(inputKey != null ? inputKey.toString() : null);
    }

    public String getInputKey() {
        return inputKey;
    }

    public GenerateSubproblems withOutputKey(String outputKey) {
        this.outputKey = outputKey;
        return this;
    }

    public<T extends Enum<?>> GenerateSubproblems withOutputKey(T outputKey) {
        return withOutputKey(outputKey != null ? outputKey.toString() : null);
    }

    public String getOutputKey() {
        return outputKey;
    }

    private void writeSubproblemLocatorsToPayload(int index, SubprocessRecord subprocess) {
        if (index <= 0) throw new RuntimeException("Invalid index %s for subproblem record".formatted(index));
        if (subprocess == null || StringUtils.isEmpty(subprocess.name))
            throw new RuntimeException("Invalid subprocess record, cannot write payload data");

        // Write subproblem index -> subprocess name locator
        getPayload().put(
                SubproblemDecomposition.getSubproblemToSubprocessDataKey(index),
                subprocess.name
        );

        // Write subprocess name -> subproblem index locator
        getPayload().put(
                SubproblemDecomposition.getSubprocessNameToSubproblemDataKey(subprocess.name),
                index
        );
    }

    private static Pattern endTagPattern(String subprocessName) {
        final String endTagRegex =
                (subprocessName == null || subprocessName.isEmpty())
                        ? "\\(END(?=[^A-Za-z0-9]|$)"
                        : "\\(END\\s+" + Pattern.quote(subprocessName) + "(?=[^A-Za-z0-9]|$)";

        return Pattern.compile(endTagRegex, Pattern.CASE_INSENSITIVE);
    }

}
