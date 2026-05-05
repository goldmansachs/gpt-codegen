package org.rj.modelgen.bpmn.component;

import org.rj.modelgen.bpmn.intrep.model.BpmnHighLevelIntermediateModel;
import org.rj.modelgen.bpmn.intrep.model.ElementHighLevelNode;
import org.rj.modelgen.llm.component.ComponentLibrarySelector;
import org.rj.modelgen.llm.exception.LlmGenerationModelException;
import org.rj.modelgen.llm.intrep.IntermediateModelParser;
import org.rj.modelgen.llm.models.generation.multilevel.data.MultiLevelModelStandardPayloadData;
import org.rj.modelgen.llm.state.ModelInterfacePayload;

import java.util.stream.Collectors;

import static org.rj.modelgen.bpmn.component.BpmnComponentLibrary.fullLibrary;
import static org.rj.modelgen.llm.models.generation.multilevel.data.MultiLevelModelStandardPayloadData.HighLevelModel;
import static org.rj.modelgen.llm.models.generation.multilevel.data.MultiLevelModelStandardPayloadData.ReverseRenderedIntermediateModel;

public class BpmnComponentLibraryDetailLevelSelector implements ComponentLibrarySelector<BpmnComponentLibrary> {
    @Override
    public BpmnComponentLibrary getFilteredLibrary(BpmnComponentLibrary baseLibrary, ModelInterfacePayload payload) {
        if (payload.get(ReverseRenderedIntermediateModel) != null) {
            return fullLibrary();
        } else if (payload.get(HighLevelModel) != null) {
            final var model = getLatestHighLevelModel(payload);

            // Filter down to only the types in use in the high level model
            final var typesInUse = model.getNodes().stream()
                    .map(ElementHighLevelNode::getElementType)
                    .collect(Collectors.toSet());

            return new BpmnComponentLibrary(baseLibrary.getFilteredLibrary(comp -> typesInUse.contains(comp.getName())).getComponents());
        } else {
            throw new LlmGenerationModelException("BPMN action library detail selector could not find any intermediate model to parse");
        }
    }

    private BpmnHighLevelIntermediateModel getLatestHighLevelModel(ModelInterfacePayload payload) {
        final String content = payload.get(MultiLevelModelStandardPayloadData.HighLevelModel);
        final var parser = new IntermediateModelParser<>(BpmnHighLevelIntermediateModel.class);

        return parser.parse(content).orElseThrow(e -> new LlmGenerationModelException(String.format(
                "Component library selector could not parse high level intermediate model: %s (content: %s)", e, content)));
    }
}
