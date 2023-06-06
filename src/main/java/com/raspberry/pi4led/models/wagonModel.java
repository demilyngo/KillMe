package com.raspberry.pi4led.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class wagonModel {
    private int index;
    private String where;
    private int wayIndex;
}
