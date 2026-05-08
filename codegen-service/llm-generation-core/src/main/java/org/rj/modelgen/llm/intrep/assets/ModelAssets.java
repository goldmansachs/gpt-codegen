package org.rj.modelgen.llm.intrep.assets;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.rj.modelgen.llm.util.Util;

import java.util.List;

public class ModelAssets<TNodeUnresolvedInput extends NodeUnresolvedInput> {

    List<TNodeUnresolvedInput> unresolvedInputs;

    public ModelAssets() {
    }

    public List<TNodeUnresolvedInput> getUnresolvedInputs() {
        return unresolvedInputs;
    }

    public void setUnresolvedInputs(List<TNodeUnresolvedInput> unresolvedInputs) {
        this.unresolvedInputs = unresolvedInputs;
    }

    @JsonIgnore
    public String serialize() {
        return Util.serializeOrThrow(this);
    }

}

