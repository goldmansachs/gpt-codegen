package org.rj.modelgen.ui.model.a2ui.util;

import org.json.JSONArray;
import org.json.JSONObject;
import org.rj.modelgen.ui.model.a2ui.type.CheckRule;
import org.rj.modelgen.ui.model.a2ui.type.DynamicBoolean;

import java.util.ArrayList;
import java.util.List;

public final class CheckableDeserializer {
    private CheckableDeserializer() {}

    public static List<CheckRule> parseChecks(JSONArray checksArray) {
        List<CheckRule> rules = new ArrayList<>();
        for (int i = 0; i < checksArray.length(); i++) {
            JSONObject ruleObj = checksArray.getJSONObject(i);
            DynamicBoolean condition = DynamicTypeDeserializer.deserializeDynamicBoolean(ruleObj.get("condition"));
            String message = ruleObj.getString("message");
            rules.add(new CheckRule(condition, message));
        }
        return rules;
    }
}
