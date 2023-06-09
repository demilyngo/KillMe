package com.raspberry.pi4led.models;

import lombok.Getter;

@Getter
public
enum Control {
    FIELD("Управление по месту"),
    SERVER("Управление с АРМ");

    private final String displayValue;

    Control(String control) {
        this.displayValue = control;
    }
}
