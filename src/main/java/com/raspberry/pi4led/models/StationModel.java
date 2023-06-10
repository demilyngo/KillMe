package com.raspberry.pi4led.models;

import com.pi4j.io.gpio.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.*;

@Getter
@Setter
@ToString
public class StationModel {
    private static final int startBitLength = 1;
    private static final int stopBitLength = 1;
    private static final int controllerLength = 2;
    private static final int taskLength = 4;

    private ArrayList<Integer> checkControllerMessages = new ArrayList<>(Arrays.asList(1, 33, 65, 97));
    private ArrayList<Integer> executionErrorIds = new ArrayList<>(Arrays.asList(1, 2, 3, 4));
    private ArrayList<Integer> connectionErrorIds = new ArrayList<>(Arrays.asList(5, 6, 7, 8));
    private Integer checkControllerMessage;
    private ArrayList<Integer> errors = new ArrayList<>(Arrays.asList(31, 63, 95, 127));
    private int errorId = 0;

    private State state;
    private Control control;

    private int sortedTrainCounter;
    private int trainCounter;
    private int currentWay = -1;
    private String nameOfStation;

    boolean sending, receiving, falseMessage;
    private static GpioPinDigitalMultipurpose pin;
    private final BitSet receivedMessage = new BitSet(8);

    private final GpioController gpioController = GpioFactory.getInstance();

    public StationModel(State state, Control control, int trainCounter, String name) {
        threadListener.start();
        this.state = state;
        this.control = control;
        this.trainCounter = trainCounter;
        this.nameOfStation = name;
        System.out.println("Constructor station");
    }

    Runnable listener = () -> {
        //first bad message
        try {
            falseMessage = true;
            sendMessage(255);
            falseMessage = false;
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        while (!receiving && !sending && !connectionErrorIds.contains(errorId)) {
            try {
                for (int i = 1; i!= 3; i++) { /////////////
                    int j = 0;
                    do { //repeat if didnt receive proper response
                        checkControllerMessage = checkControllerMessages.get(i);
                        System.out.println("I check " + (i + 1));
                        sendMessage(checkControllerMessage);
                        Thread.sleep(2000);
//                        int messageToSend = 35;
//                        int messageToReceive = 67;
//                        for (int x =0; x!=6; x++) {
//                            System.out.println("Semaphore " + (x+1));
//                            sendMessage(messageToSend);
//                            messageToSend+=2;
//                            while(convertReceived(receivedMessage) != messageToReceive) {
//                                sendMessage(checkControllerMessages.get(2));
//                                System.out.println("I want: "+ messageToReceive);
//                                System.out.println("I received: " + convertReceived(receivedMessage));
//                                Thread.sleep(2000);
//                            }
//                            messageToReceive+=2;
//                        }
                        j++;
                    } while(j != 3 && (convertReceived(receivedMessage) == 0 || convertReceived(receivedMessage) == 255) && errorId == 0);
                    if (j == 3) {
                        errorId = connectionErrorIds.get(i);
                        break;
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    };
    Thread threadListener = new Thread(listener);
    long listenerId = threadListener.getId();

    ///////////////////////////////////////
    public synchronized void sendMessage(Integer message) throws InterruptedException {
        if (!receiving && !sending) {
            setOutput();
            sending = true;
            BitSet messageBitSet = convertToBitSet(message);
            for (int i = 0; i!=8; i++) { //Integer.toBinaryString(message).toCharArray()
                if (messageBitSet.get(i)) {
                    pin.high();
                    System.out.println("Sent: " + messageBitSet.get(i));
                    Thread.sleep(10);
                    continue;
                }
                pin.low();
                System.out.println("Sent: " + messageBitSet.get(i));
                Thread.sleep(10);
            }
            pin.high();
            sending = false;
            setInput();

            receiveMessage();
        }
    }

    ///////////////////////////////////
    public void receiveMessage() throws InterruptedException {
        if (!sending && !receiving && !falseMessage) {
            receivedMessage.clear();
            receiving = true;

            long startTime = System.currentTimeMillis();
            while (pin.isHigh() && System.currentTimeMillis() - startTime < 3000) { // wait for start bit
                Thread.onSpinWait();
            }
            if(pin.isHigh()) {
                receiving = false;
                errorId = connectionErrorIds.get(checkControllerMessages.indexOf(checkControllerMessage));
                return;
            }

            receivedMessage.clear(0);
            System.out.println("Received: " + receivedMessage.get(0));
            Thread.sleep(10);
            for (int i=1; i!=startBitLength+controllerLength+taskLength+stopBitLength; i++) {
                if (pin.isLow()) {
                    receivedMessage.clear(i);
                } else {
                    receivedMessage.set(i);
                }
                System.out.println("Received: " + receivedMessage.get(i));
                Thread.sleep(10);
            }


//            System.out.println("I want: " + checkControllerMessage);
//            System.out.println("I received: " + convertReceived(receivedMessage));
            if (convertReceived(receivedMessage) == checkControllerMessage) { //controller is connected (must receive controller number)
                receiving = false;
                System.out.println("Checked successfully");
                return;
            }

            if(errors.contains(convertReceived(receivedMessage))) { //errors handler
                errorId = errors.indexOf(convertReceived(receivedMessage)) + 1;
                return;
            }


            //reaction on messages
            if (!receivedMessage.get(0) && receivedMessage.get(1)) {
                if (convertReceived(receivedMessage) == 79) {
                    //sensors
                    if (this.state == State.WAITING) {
                        trainCounter++;
                    }

                    if (this.state == State.SORTING) {
                        trainCounter--;
                    }
                }
                else if (convertReceived(receivedMessage) >= 67 && convertReceived(receivedMessage) <= 77) {
                    sortedTrainCounter ++;
                    System.out.println("Train counter: " + sortedTrainCounter);
                    trainCounter --;
                }
                receiving = false;
                return;
            }
            if (getControl() == Control.FIELD) {
                if (convertReceived(receivedMessage) > 97) { //stand buttons
                    switch (convertReceived(receivedMessage)) {
                        case 99 -> {
                            sendMessage(35); //semaphore way 1
                            sendMessage(3); //rails way 1
                            currentWay = 1;
                        }
                        case 101 -> {
                            sendMessage(37); //semaphore way 2
                            sendMessage(5); //rails way 2
                            currentWay = 2;
                        }
                        case 103 -> {
                            sendMessage(39); //semaphore way 3
                            sendMessage(7); //rails way 3
                            currentWay = 3;
                        }
                        case 105 -> {
                            sendMessage(41); //semaphore way 4
                            sendMessage(9); //rails way 4
                            currentWay = 4;
                        }
                        case 107 -> {
                            sendMessage(43); //semaphore way 5
                            sendMessage(11); //rails way 5
                            currentWay = 5;
                        }
                        case 109 -> {
                            sendMessage(45); //semaphore way 6
                            sendMessage(13); //rails way 6
                            currentWay = 6;
                        }
                        case 111 -> {
                            sendMessage(15); //to vitazhnoy put
                        }
                        case 113 -> {
                            sendMessage(17);//to depo
                        }
                    }
                }
            }
            receiving = false;
        }
    }


    public static Integer convertReceived(BitSet bits) {
        int value = 0;
        for (int i = 0; i != 8; i++) {
            value += bits.get(i) ? (1 << 7-i) : 0;
        }
        return value;
    }

    public static BitSet convertToBitSet(Integer message) {
        BitSet resMessage = new BitSet(8);
        int pos = 0;
        //StringBuilder resMessage = new StringBuilder();
        System.out.println(Integer.toBinaryString(message).length());
        for (int i = 0; i!= 8-Integer.toBinaryString(message).length(); i++) {
            resMessage.clear(pos);
            pos++;
        }
        for(char bit : Integer.toBinaryString(message).toCharArray()) {
            if(bit == '1') {
                resMessage.set(pos);
            }
            else {
                resMessage.clear(pos);
            }
            pos++;
        }
        return resMessage;
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


}