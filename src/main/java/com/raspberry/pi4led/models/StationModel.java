package com.raspberry.pi4led.models;

import com.pi4j.io.gpio.*;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Getter
@Setter
public class StationModel {
    private final int startBitLength = 1;
    private final int stopBitLength = 1;
    private final int controllerLength = 2;
    private final int taskLength = 4;
    private final int messageLength = startBitLength+controllerLength+taskLength+stopBitLength;

    private ArrayList<Integer> checkControllerMessages = new ArrayList<>(Arrays.asList(1, 33, 65, 97));
    private Integer checkControllerMessage;
    private ArrayList<Integer> executionErrorIds = new ArrayList<>(Arrays.asList(1, 2, 3, 4));
    private ArrayList<Integer> connectionErrorIds = new ArrayList<>(Arrays.asList(5, 6, 7, 8));
    private ArrayList<Integer> errors = new ArrayList<>(Arrays.asList(31, 63, 95, 127));
    private Integer errorId = 0;

    private State state;
    private Control control;


    private int trainCounter;
    private int currentWay = 8;
    private String nameOfStation;

    boolean isSending, isReceiving, isFalseMessage;
    private final BitSet receivedMessage = new BitSet(8);
    private static GpioPinDigitalMultipurpose pin;
    private final GpioController gpioController = GpioFactory.getInstance();

    ArrayList<String> cities = new ArrayList<String>(Arrays.asList("Москва", "Казань", "Магадан", "Воркута", "Якутск", "Тюмень"));
    ArrayList<Integer> counters = new ArrayList<Integer>(Arrays.asList(0, 0, 0, 0, 0, 0));
    ArrayList<wagonModel> wagonList = new ArrayList<wagonModel>();

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

    public BitSet convertToBitSet(Integer message) {
        BitSet resMessage = new BitSet(messageLength);
        int pos = 0;
        for (int i = 0; i!= messageLength-Integer.toBinaryString(message).length(); i++) {
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

    public Integer convertReceived(BitSet bits) {
        int value = 0;
        for (int i = 0; i != messageLength; i++) {
            value += bits.get(i) ? (1 << 7-i) : 0;
        }
        return value;
    }

    public synchronized void sendMessage(Integer message) throws InterruptedException {
        setOutput();
        BitSet messageBitSet = convertToBitSet(message);
        for (int i = 0; i!=messageLength; i++) {
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
        setInput();
        receiveMessage();
    }


    public void receiveMessage() throws InterruptedException {
        receivedMessage.clear();
        long startTime = System.currentTimeMillis();
        while (pin.isHigh() && System.currentTimeMillis() - startTime < 3000) { // wait for start bit
            Thread.onSpinWait();
        }
        if(pin.isHigh()) {
            errorId = connectionErrorIds.get(checkControllerMessages.indexOf(checkControllerMessage));
            return;
        }
        receivedMessage.clear(0);
        System.out.println("Received: " + receivedMessage.get(0));
        Thread.sleep(10);
        for (int i=1; i!=messageLength; i++) {
            if (pin.isLow()) {
                receivedMessage.clear(i);
            } else {
                receivedMessage.set(i);
            }
            System.out.println("Received: " + receivedMessage.get(i));
            Thread.sleep(10);
        }

        if (convertReceived(receivedMessage) == 0) { //controller is connected
            System.out.println("Checked successfully");
            return;
        }
        if(errors.contains(convertReceived(receivedMessage))) { //errors handler
            errorId = executionErrorIds.get(checkControllerMessages.indexOf(checkControllerMessage));
            return;
        }
        //reaction on messages
        if (!receivedMessage.get(0) && receivedMessage.get(2)) {
            if (convertReceived(receivedMessage) >65 && convertReceived(receivedMessage) < 79) {
                counters.set(currentWay-1, counters.get(currentWay-1) + 1); // counters at the ends
            }
            else if (convertReceived(receivedMessage) == 79) { //counter at the start
                if (this.state == State.COMING) {
                    trainCounter++;
                    wagonModel newWagon = new wagonModel(trainCounter, cities.get(0), 0);
                    wagonList.add(newWagon);
                }
                else if (this.state == State.SORTING) {
                    trainCounter--;
                    wagonList.remove(trainCounter);
                }
            }
            else if (getControl() == Control.FIELD) {
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
                    case 113 -> {
                        sendMessage(47); //semaphore to depot
                        sendMessage(17); //rails to depot
                        currentWay = 8;
                    }
                }
            }
        }
    }


    Runnable listener = () -> {
        //first bad message
        try {
            isFalseMessage = true;
            sendMessage(255);
            isFalseMessage = false;
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        while (true) {
            try {
                for (int i = 0; i!= 4; i++) { /////////////
                    int j = 0;
                    do { //repeat if didnt receive proper response
                        System.out.println("Checking " + i);
                        checkControllerMessage = checkControllerMessages.get(i);
                        sendMessage(checkControllerMessage);
                        j++;
                    } while(j != 3
                            && (convertReceived(receivedMessage) != 0)
                            && errorId == 0); //menat?
                    if (j == 3) {
                        errorId = connectionErrorIds.get(i); // menat
                        break;
                    }
                }
                if(connectionErrorIds.contains(errorId)) {
                    break;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    };
    Thread threadListener = new Thread(listener);
    long listenerId = threadListener.getId();

    public StationModel(State state, Control control, String name, Integer counter) {
        this.state = state;
        this.control = control;
        this.nameOfStation = name;
        this.trainCounter = counter;
        threadListener.start();
    }
}