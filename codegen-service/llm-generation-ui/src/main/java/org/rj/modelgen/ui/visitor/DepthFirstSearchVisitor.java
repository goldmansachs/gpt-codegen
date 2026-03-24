package org.rj.modelgen.ui.visitor;

import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.component.*;
import org.rj.modelgen.ui.model.a2ui.type.ChildList;
import org.rj.modelgen.ui.model.a2ui.type.StaticChildList;
import org.rj.modelgen.ui.model.a2ui.type.TemplateChildList;
import org.rj.modelgen.ui.model.a2ui.util.A2UIComponentVisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DepthFirstSearchVisitor implements A2UIComponentVisitor<List<String>> {
    private final Map<String, A2UIComponent<?>> componentMap;

    public DepthFirstSearchVisitor(Map<String, A2UIComponent<?>> componentMap) {
        this.componentMap = componentMap;
    }

    /**
     * Entry point: start traversal from the root component.
     * Per the specification, there must be exactly one component with id "root".
     */
    public List<String> traverse() {
        A2UIComponent<?> root = componentMap.get("root");
        if (root == null) {
            return Collections.emptyList();
        }
        return root.accept(this);
    }

    // --- Helper methods ---

    private List<String> visitLeaf(A2UIComponent<?> component) {
        return List.of(component.getId());
    }

    private List<String> visitChildList(A2UIComponent<?>parent, ChildList children) {
        List<String> result = new ArrayList<>();
        result.add(parent.getId());

        if (children instanceof StaticChildList staticList) {
            for (String childId : staticList.componentIds()) {
                A2UIComponent<?>child = componentMap.get(childId);
                if (child != null) {
                    result.addAll(child.accept(this));
                }
            }
        } else if (children instanceof TemplateChildList template) {
            // For template children, visit the template component itself.
            // At runtime, the client would iterate over the data at template.path()
            // and instantiate the template for each item.
            A2UIComponent<?>templateComponent = componentMap.get(template.componentId());
            if (templateComponent != null) {
                result.addAll(templateComponent.accept(this));
            }
        }

        return result;
    }

    private List<String> visitSingleChild(A2UIComponent<?>parent, String childId) {
        List<String> result = new ArrayList<>();
        result.add(parent.getId());
        if (childId != null) {
            A2UIComponent<?>child = componentMap.get(childId);
            if (child != null) {
                result.addAll(child.accept(this));
            }
        }
        return result;
    }

    // --- Display components (leaf nodes) ---

    @Override
    public List<String> visitText(TextComponent text) {
        return visitLeaf(text);
    }

    @Override
    public List<String> visitImage(ImageComponent image) {
        return visitLeaf(image);
    }

    @Override
    public List<String> visitIcon(IconComponent icon) {
        return visitLeaf(icon);
    }

    @Override
    public List<String> visitVideo(VideoComponent video) {
        return visitLeaf(video);
    }

    @Override
    public List<String> visitAudioPlayer(AudioPlayerComponent audioPlayer) {
        return visitLeaf(audioPlayer);
    }

    @Override
    public List<String> visitDivider(DividerComponent divider) {
        return visitLeaf(divider);
    }

    // --- Layout containers (with ChildList children) ---

    @Override
    public List<String> visitRow(RowComponent row) {
        return visitChildList(row, row.getChildren());
    }

    @Override
    public List<String> visitColumn(ColumnComponent column) {
        return visitChildList(column, column.getChildren());
    }

    @Override
    public List<String> visitList(ListComponent list) {
        return visitChildList(list, list.getChildren());
    }

    // --- Containers with single child (ComponentId reference) ---

    @Override
    public List<String> visitCard(CardComponent card) {
        return visitSingleChild(card, card.getChild());
    }

    @Override
    public List<String> visitButton(ButtonComponent button) {
        return visitSingleChild(button, button.getChild());
    }

    // --- Tabs: each tab has a title and a child ComponentId ---

    @Override
    public List<String> visitTabs(TabsComponent tabs) {
        List<String> result = new ArrayList<>();
        result.add(tabs.getId());
        if (tabs.getTabs() != null) {
            for (TabItem tab : tabs.getTabs()) {
                if (tab.getChild() != null) {
                    A2UIComponent<?>child = componentMap.get(tab.getChild());
                    if (child != null) {
                        result.addAll(child.accept(this));
                    }
                }
            }
        }
        return result;
    }

    // --- Modal: has trigger and content, both ComponentId references ---

    @Override
    public List<String> visitModal(ModalComponent modal) {
        List<String> result = new ArrayList<>();
        result.add(modal.getId());
        if (modal.getTrigger() != null) {
            A2UIComponent<?>trigger = componentMap.get(modal.getTrigger());
            if (trigger != null) {
                result.addAll(trigger.accept(this));
            }
        }
        if (modal.getContent() != null) {
            A2UIComponent<?>content = componentMap.get(modal.getContent());
            if (content != null) {
                result.addAll(content.accept(this));
            }
        }
        return result;
    }

    // --- Input components (leaf nodes with no structural children) ---

    @Override
    public List<String> visitTextField(TextFieldComponent textField) {
        return visitLeaf(textField);
    }

    @Override
    public List<String> visitCheckBox(CheckBoxComponent checkBox) {
        return visitLeaf(checkBox);
    }

    @Override
    public List<String> visitChoicePicker(ChoicePickerComponent choicePicker) {
        return visitLeaf(choicePicker);
    }

    @Override
    public List<String> visitSlider(SliderComponent slider) {
        return visitLeaf(slider);
    }

    @Override
    public List<String> visitDateTimeInput(DateTimeInputComponent dateTimeInput) {
        return visitLeaf(dateTimeInput);
    }
}


