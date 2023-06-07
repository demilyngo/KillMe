package com.raspberry.pi4led.controllers;

import com.pi4j.io.gpio.*;

import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Getter
enum State{
    WAITING("Ожидание"),
    COMING("Прибытие"),
    READY("Готово к сортировке"),
    SORTING("Сортировка"),
    LEAVING("Отбытие");


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

    private Integer checkController1 = 128;
    private Integer checkController2 = 160;
    private Integer checkController3 = 192;
    private Integer checkController4 = 224;
    private Integer checkControllerMessage;
    private ArrayList<Integer> errors = new ArrayList<Integer>(Arrays.asList(158, 190, 222, 254));
    private int errorId = 0;

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
//                checkControllerMessage = checkController2;
//                System.out.println("I check 2");
//                sendMessage(checkControllerMessage);
//                checkControllerMessage = checkController3;
//                System.out.println("I check 3");
//                sendMessage(checkControllerMessage);
//                if(getControl() == Control.FIELD) {
//                    System.out.println("I check 4");
//                    checkControllerMessage = checkController4;
//                    sendMessage(checkControllerMessage);
//                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    };
    Thread thread = new Thread(listener);
    long listenerId = thread.getId();


    public void receiveMessage() throws InterruptedException {
        if (!sending && !receiving) {
            receivedMessage.clear();
            receiving = true;
            Thread.sleep(1000);
            long startTime = System.currentTimeMillis();
            while (pin.isLow()) {
                if(System.currentTimeMillis() - startTime > 5000) {
                    //errorId = checkControllerMessage/32 - 3;
                    return;
                }
                Thread.onSpinWait();
            }
            receivedMessage.set(0);
            System.out.println("Received: " + receivedMessage.get(0));

            for (int i=1; i!=startBitLength+startBitLength+controllerLength+taskLength; i++) {
                if (pin.isHigh()) {
                    receivedMessage.set(i);
                } else {
                    receivedMessage.clear(i); ///CHECK STOP BIT ingore
                }
                System.out.println("Received: " + receivedMessage.get(i));
                Thread.sleep(1000);
            }

            System.out.println(convertReceived(receivedMessage));
            System.out.println(checkControllerMessage);
            if (convertReceived(receivedMessage) == checkControllerMessage) { //controller is connected (must receive controller number)
                receiving = false;
                System.out.println("Checked successfully");
                return;
            }

            if(errors.contains(convertReceived(receivedMessage))) {
                errorId = errors.indexOf(convertReceived(receivedMessage)) + 1;
                return;
            }


            //reaction on messages
            if (receivedMessage.get(0) && receivedMessage.get(1)) {
                if (convertReceived(receivedMessage) == 206) {
                    //sensors
                    if (this.state == State.WAITING) {
                        trainCounter++;
                    }

                    if (this.state == State.SORTING) {
                        trainCounter--;
                    }
                }
                else if (convertReceived(receivedMessage) >= 194 && convertReceived(receivedMessage) <= 204) {
                    sortedTrainCounter ++;
                    trainCounter --;
                }
                receiving = false;
                return;
            }
            if (getControl() == Control.FIELD) {
                if (convertReceived(receivedMessage) > 224) { //stand buttons
                    switch (convertReceived(receivedMessage)) {
                        case 226 -> {
                            sendMessage(162); //semaphore way 1
                            sendMessage(130); //rails way 1
                            currentWay = 1;
                        }
                        case 228 -> {
                            sendMessage(164); //semaphore way 2
                            sendMessage(132); //rails way 2
                            currentWay = 2;
                        }
                        case 230 -> {
                            sendMessage(166); //semaphore way 3
                            sendMessage(134); //rails way 3
                            currentWay = 3;
                        }
                        case 232 -> {
                            sendMessage(168); //semaphore way 4
                            sendMessage(134); //rails way 4
                            currentWay = 4;
                        }
                        case 234 -> {
                            sendMessage(170); //semaphore way 5
                            sendMessage(136); //rails way 5
                            currentWay = 5;
                        }
                        case 236 -> {
                            sendMessage(172); //semaphore way 6
                            sendMessage(138); //rails way 6
                            currentWay = 6;
                        }
                        case 238 -> {
                            sendMessage(142); //to vitazhnoy put
                        }
                        case 240 -> {
                            sendMessage(144);//to depo
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
                    Thread.sleep(1000);
                    continue;
                }
                pin.low();
                System.out.println("Sent: " + bit);
                Thread.sleep(1000);
            }
            pin.low();
            sending = false;
            setInput();
            //if checking for input then receive. else sending from application
            receiveMessage();
        }
    }

    public static Integer convertReceived(BitSet bits) {
        int value = 0;
        for (int i = 0; i != bits.length(); i++) {
            value += bits.get(i) ? (1 << i) : 0;
        }
        return value;
    }

    public static String convertToBitSet(Integer message) {
        StringBuilder resMessage = new StringBuilder();
        for(char bit : Integer.toBinaryString(message).toCharArray()) {
            if(bit == '1') {
                resMessage.append("1");
            }
            else {
                resMessage.append("0");
            }
        }
        return resMessage.toString();
    }


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