package com.raspberry.pi4led.models;

import lombok.Getter;

@Getter
public
enum State {
    WAITING("Ожидание"),
    COMING("Прибытие"),
    READY("Готово к сортировке"),
    SORTING("Сортировка"),
    SORTED("Отсортировано"),
    LEAVING("Отбытие");

    private final String displayValue;

    State(String state) {
        this.displayValue = state;
    }
}
