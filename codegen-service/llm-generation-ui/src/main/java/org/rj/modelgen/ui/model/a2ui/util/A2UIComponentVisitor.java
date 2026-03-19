package org.rj.modelgen.ui.model.a2ui.util;

import org.rj.modelgen.ui.model.a2ui.component.*;

public interface A2UIComponentVisitor<T> {

    // Display components
    T visitText(TextComponent text);

    T visitImage(ImageComponent image);

    T visitIcon(IconComponent icon);

    T visitVideo(VideoComponent video);

    T visitAudioPlayer(AudioPlayerComponent audioPlayer);

    // Layout containers
    T visitRow(RowComponent row);

    T visitColumn(ColumnComponent column);

    T visitList(ListComponent list);

    T visitCard(CardComponent card);

    T visitTabs(TabsComponent tabs);

    T visitModal(ModalComponent modal);

    T visitDivider(DividerComponent divider);

    // Interactive / input components
    T visitButton(ButtonComponent button);

    T visitTextField(TextFieldComponent textField);

    T visitCheckBox(CheckBoxComponent checkBox);

    T visitChoicePicker(ChoicePickerComponent choicePicker);

    T visitSlider(SliderComponent slider);

    T visitDateTimeInput(DateTimeInputComponent dateTimeInput);
}