package com.raspberry.pi4led.controllers;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.Console;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Controller
public class WebController {
    final StationController stationController = new StationController(State.WAITING, Control.SERVER, 3, "Сургутская");

    @GetMapping("/")
    public String greeting(Model model) throws InterruptedException {
        ArrayList<StationController> station = new ArrayList<StationController>();
        station.add(stationController);
        model.addAttribute("station", station);

        ArrayList<String> cities = new ArrayList<String>(Arrays.asList("Москва", "Казань", "Нижневартовск", "Воркута"));
        model.addAttribute("cities", cities);

        ArrayList<wagonModel> wagonList = new ArrayList<wagonModel>();
        for(int i=1; i!=stationController.getTrainCounter()+1; i++) {
            int way = (int) (Math.random() * 4);
            wagonList.add(new wagonModel(i, cities.get(way), way));
        }
        model.addAttribute("wagonList", wagonList);
        return "index";
    }

    private static final String[] MAPS = "Screenshot_1 Screenshot_2 Screenshot_3 Screenshot_4 Screenshot_5 Screenshot_6".split(" ");
    private final ExecutorService cachedThreadPool = Executors.newCachedThreadPool();
    //private final ExecutorService fixedThreadPool = Executors.newFixedThreadPool(4);
    @GetMapping(path = "/start", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter getWords(@RequestParam(value = "order", defaultValue = "0") String order) {
        stationController.setState(State.SORTING);
        SseEmitter emitter = new SseEmitter();
        SseEmitter.SseEventBuilder eventBuilder = SseEmitter.event();
        cachedThreadPool.execute(() -> {
            try {
                for(char way : order.toCharArray()) {
                    System.out.println("SEMAPHORE " + way);
                    stationController.sendMessage(256 + 2); //message to change semaphores
                    //eventBuilder.id("5").data(stationController.getState()).build();
                    eventBuilder.id("1").data(MAPS[way]).build();
                    emitter.send(eventBuilder);


//                    i = (int) (Math.random() * 5);
//                    eventBuilder.id("2").data(MAPS[i]).build();
//                    emitter.send(eventBuilder);
                }
                eventBuilder.id("2").data("Done sorting").build();
                emitter.send(eventBuilder);
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;

//        SseEmitter emitter = new SseEmitter();
//        SseEmitter.SseEventBuilder eventBuilder = SseEmitter.event();
//        if(stationController.getState() != State.SORTING) { //to stop threads from multiplying
//            stationController.setState(State.SORTING);
//            cachedThreadPool.execute(() -> {
//                System.out.println("INSIDE SSE");
//                for (char way : order.toCharArray()) {
//                    System.out.println("INSIDE LOOP");
//                    //stationController.setCurrentWay(way);
//                    try {
//                        emitter.send("inside try");
//                        eventBuilder.id("1").data(MAPS[way]).build();
//                        emitter.send(eventBuilder);
//                        emitter.send("Before sending");
//                        System.out.println("SEMAPHORE");
//                        stationController.sendMessage(256 + 2 * way); //message to change semaphores
//                        emitter.send("Sent semaphore");
//                        System.out.println("WAY");
//                        stationController.sendMessage(320 + 2 * way); //message to change way
//                        emitter.send("Sent way");
//                        System.out.println("MOVING");
//                        stationController.sendMessage(336); //start moving
//                        emitter.send("After sending");
////                        while (StationController.convertReceived(stationController.getReceivedMessage()) != 384 + 2 * way) {
////                            Thread.onSpinWait();
////                        }
//                    } catch (IOException | InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }
//
//                try {
//                    eventBuilder.id("2").data("Finished sorting").build();
//                    emitter.send(eventBuilder);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            });
//        }
//        return emitter;
    }


    @ResponseBody
    @GetMapping("/wait")
    public SseEmitter startSorting()  {
        SseEmitter emitter = new SseEmitter();
        SseEmitter.SseEventBuilder eventBuilder = SseEmitter.event();
        if(stationController.getState() == State.WAITING) {
            stationController.setState(State.COMING);
            cachedThreadPool.execute(() -> {
                try {
                    emitter.send("inWaiting");
                    stationController.sendMessage(334); //moving to position for sorting
//                    while (StationController.convertReceived(stationController.getReceivedMessage()) != 398
//                            || StationController.convertReceived(stationController.getReceivedMessage()) != 400) {
//                        Thread.onSpinWait();
//                    }
                    if (StationController.convertReceived(stationController.getReceivedMessage()) == 398) {
                        stationController.setTrainCounter(stationController.getTrainCounter() + 1);
                        eventBuilder.id("1").data(stationController.getTrainCounter()).build();
                        emitter.send(eventBuilder);
                    } else {
                        eventBuilder.id("2").data("Ready to sort").build();
                        emitter.send(eventBuilder);
                    }
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            });
        }
        return emitter;
    }
}