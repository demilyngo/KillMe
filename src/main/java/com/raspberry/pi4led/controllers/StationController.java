package com.raspberry.pi4led.controllers;

import com.pi4j.io.gpio.*;

import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.BitSet;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Getter
enum State{
    WAITING("Ожидание"),
    SORTING("Сортировка"),
    LEAVING("Отбытие"),
    COMING("Прибытие");

    private final String displayValue;
    State(String state) {
        this.displayValue = state;
    }
}
@Getter
enum Control {
    FIELD("Управление по месту"),
    SERVER("Управление с АРМ");

    private final String displayValue;
    Control(String control) {
        this.displayValue = control;
    }
}

@Getter
@Setter
@ToString
public class StationController {
    private static final int startBitLength = 1;
    private static final int stopBitLength = 1;
    private static final int controllerLength = 2;
    private static final int taskLength = 4;

    private Integer checkController1 = 256;
    private Integer checkController2 = 320;
    private Integer checkController3 = 384;
    private Integer checkController4 = 448;
    private Integer checkControllerMessage;

    private State state;
    private Control control;

    private int sortedTrainCounter;
    private int trainCounter;
    private int currentWay = -1;
    private String nameOfStation;

    boolean sending, receiving;
    private static GpioPinDigitalMultipurpose pin;
    private final BitSet receivedMessage = new BitSet(8);

    private final GpioController gpioController = GpioFactory.getInstance();

    Runnable listener = () -> {
        while (!receiving && !sending) {
            try {
                checkControllerMessage = checkController1;
                System.out.println("I check 1");
                sendMessage(checkControllerMessage);
                checkControllerMessage = checkController2;
                System.out.println("I check 2");
                sendMessage(checkControllerMessage);
                checkControllerMessage = checkController3;
                System.out.println("I check 3");
                sendMessage(checkControllerMessage);
                if(getControl() == Control.FIELD) {
                    System.out.println("I check 4");
                    checkControllerMessage = checkController4;
                    sendMessage(checkControllerMessage);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    };
    Thread thread = new Thread(listener);
    long listenerId = thread.getId();


    public synchronized void receiveMessage() throws InterruptedException {
        if (!sending && !receiving) {
            receivedMessage.clear();
            receiving = true;
            for (int i=0; i!=startBitLength+startBitLength+controllerLength+taskLength; i++) {
                if (pin.isHigh()) {
                    receivedMessage.set(i);
                } else {
                    receivedMessage.clear(i); ///CHECK STOP BIT ingore
                }
                System.out.println("Received: " + receivedMessage.get(i));
                Thread.sleep(100);
            }
//            if(receivedMessage.get(startBitLength+startBitLength+controllerLength+taskLength-1)) { //?????? chto eto?
//                receivedMessage.clear();
//                receiving = false;
//                return;
//            }

            if (convertReceived(receivedMessage) == 0) { //controller is connected
                receiving = false;
                System.out.println("Checked successfully");
                return;
            }

            //reaction on messages
            if (receivedMessage.get(0) && receivedMessage.get(1)) {
                if (convertReceived(receivedMessage) == 400) {
                    //sensors
                    if (this.state == State.WAITING) {
                        trainCounter++;
                    }

                    if (this.state == State.SORTING) {
                        trainCounter--;
                    }
                }
                if(convertReceived(receivedMessage) >= 386) {
                    sortedTrainCounter ++;
                    trainCounter --;
                }
                receiving = false;
                return;
            }
            if (getControl() == Control.FIELD) {
                if (convertReceived(receivedMessage) > 448) { //stand buttons
                    switch (convertReceived(receivedMessage)) {
                        case 450 -> {
                            sendMessage(258); //semaphore way 1
                            sendMessage(322); //rails way 1
                            currentWay = 1;
                        }
                        case 452 -> {
                            sendMessage(260); //semaphore way 2
                            sendMessage(324); //rails way 2
                            currentWay = 2;
                        }
                        case 454 -> {
                            sendMessage(262); //semaphore way 3
                            sendMessage(326); //rails way 3
                            currentWay = 3;
                        }
                        case 456 -> {
                            sendMessage(264); //semaphore way 4
                            sendMessage(328); //rails way 4
                            currentWay = 4;
                        }
                        case 458 -> {
                            sendMessage(266); //semaphore way 5
                            sendMessage(330); //rails way 5
                            currentWay = 5;
                        }
                        case 460 -> {
                            sendMessage(268); //semaphore way 6
                            sendMessage(332); //rails way 6
                            currentWay = 6;
                        }
                        case 462 -> {
                            sendMessage(270); //toggle lights
                        }
                        case 464 -> {
                            sendMessage(346);//start moving
                        }
                    }
                }
            }
            receiving = false;
        }
    }
    ///////////////////////////////////////
    public synchronized void sendMessage(Integer message) throws InterruptedException {
        if (!receiving && !sending) {
            setOutput();
            pin.low();
            sending = true;
            for (char bit : Integer.toBinaryString(message).toCharArray()) { //Integer.toBinaryString(message).toCharArray()
                if (bit == '1') {
                    pin.high();
                    System.out.println("Sent: " + bit);
                    Thread.sleep(100);
                    continue;
                }
                pin.low();
                System.out.println("Sent: " + bit);
                Thread.sleep(100);
            }
            pin.low();
            sending = false;
            setInput();
            if(message.equals(checkControllerMessage)) { //if checking for input then receive. else sending from application
                receiveMessage();
            }
        }
    }

    public static Integer convertReceived(BitSet bits) {
        int value = 0;
        for (int i = 0; i != bits.length(); i++) {
            value += bits.get(i) ? (1 << i) : 0;
        }
        return value;
    }

//    public static String convertToBitSet(Integer message) {
//        StringBuilder resMessage = new StringBuilder();
//        int pos = startBitLength+startBitLength+controllerLength+taskLength - Integer.toBinaryString(message).length();
//        for(int i = 0; i!=startBitLength+startBitLength+controllerLength+taskLength - Integer.toBinaryString(message).length(); i++) {
//            resMessage.append("0");
//        }
//        for(char bit : Integer.toBinaryString(message).toCharArray()) {
//            if(bit == '1') {
//                resMessage.append("1");
//            }
//            else {
//                resMessage.append("0");
//            }
//            pos++;
//        }
//        return resMessage.toString();
//    }


    public void setInput() {
        if (pin == null) {
            pin = gpioController.provisionDigitalMultipurposePin(RaspiPin.GPIO_01, PinMode.DIGITAL_INPUT);
        }
        if (pin.getMode() == PinMode.DIGITAL_OUTPUT) {
            pin.setMode(PinMode.DIGITAL_INPUT);
        }
    }

    public void setOutput() {
        if (pin == null) {
            pin = gpioController.provisionDigitalMultipurposePin(RaspiPin.GPIO_01, PinMode.DIGITAL_OUTPUT);
        }
        if (pin.getMode() == PinMode.DIGITAL_INPUT) {
            pin.setMode(PinMode.DIGITAL_OUTPUT);
        }
    }


    StationController(State state, Control control, int trainCounter, String name) {
        setInput();
        thread.start();
        this.state = state;
        this.control = control;
        this.trainCounter = trainCounter;
        this.nameOfStation = name;
        System.out.println("Constructor station");
    }
}