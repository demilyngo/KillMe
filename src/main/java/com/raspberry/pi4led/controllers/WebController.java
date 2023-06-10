package com.raspberry.pi4led.controllers;

import com.raspberry.pi4led.models.Control;
import com.raspberry.pi4led.models.State;
import com.raspberry.pi4led.models.StationModel;
import com.raspberry.pi4led.models.wagonModel;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Controller
public class WebController {
    final StationModel stationModel = new StationModel(State.WAITING, Control.FIELD, 3, "Сургутская");
    @GetMapping("/")
    public String greeting(Model model) throws InterruptedException {
        ArrayList<StationModel> station = new ArrayList<StationModel>();
        station.add(stationModel);
        model.addAttribute("station", station);

        ArrayList<String> cities = new ArrayList<String>(Arrays.asList("Москва", "Казань", "Нижневартовск", "Воркута"));
        model.addAttribute("cities", cities);

        ArrayList<wagonModel> wagonList = new ArrayList<wagonModel>();
        for(int i = 1; i!= stationModel.getTrainCounter()+1; i++) {
            int way = (int) (Math.random() * 4);
            wagonList.add(new wagonModel(i, cities.get(way), way));
        }
        model.addAttribute("wagonList", wagonList);
        return "index";
    }

    @ResponseBody
    @GetMapping("/wait")
    public SseEmitter startSorting()  {
        SseEmitter emitter = new SseEmitter();
        SseEmitter.SseEventBuilder eventBuilder = SseEmitter.event();
        if(stationModel.getState() == State.WAITING) {
            stationModel.setState(State.COMING);
            cachedThreadPool.execute(() -> {
                try {
                    emitter.send("inWaiting");
                    stationModel.sendMessage(15); //moving to position for sorting
                    while (StationModel.convertReceived(stationModel.getReceivedMessage()) != 81) {
                        if (StationModel.convertReceived(stationModel.getReceivedMessage()) == 79) {
                            eventBuilder.id("1").data(stationModel.getTrainCounter()).build();
                            emitter.send(eventBuilder);
                        }
                    }
                    stationModel.setState(State.READY);
                    eventBuilder.id("2").data("Ready to sort").build();
                    emitter.send(eventBuilder);

                    if(stationModel.getErrorId() != 0) {
                        eventBuilder.id("3").data(stationModel.getErrorId()); //to open modal with error
                        emitter.send(eventBuilder);
                    }
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            });
        }
        return emitter;
    }

    private final ExecutorService cachedThreadPool = Executors.newCachedThreadPool();
    //private final ExecutorService fixedThreadPool = Executors.newFixedThreadPool(4);
    @GetMapping(path = "/start", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter getWords(@RequestParam(value = "order", defaultValue = "0") String order) {
        stationModel.setState(State.SORTING);
        SseEmitter emitter = new SseEmitter();
        SseEmitter.SseEventBuilder eventBuilder = SseEmitter.event();
        cachedThreadPool.execute(() -> {
            try {
                for(char way : order.toCharArray()) {
                    System.out.println("Way before: " + way);
                    way += 1;
                    System.out.println("Way after: " + (2 * way));
                    System.out.println("Way after: " + (33 + (2 * way)));
                    int msg = 33 + (2 * (int)way);
                    System.out.println("SEMAPHORE " + way + " Message: " + msg);
                    stationModel.sendMessage(msg); //message to change semaphores
                    //stationModel.sendMessage(130 + 2 * way); //message to change arrows
                    eventBuilder.id("1").data("Map_" + way).build();
                    emitter.send(eventBuilder);
                    if (stationModel.getErrorId() != 0) {
                        eventBuilder.id("3").data(stationModel.getErrorId()); //to open modal with error
                        emitter.send(eventBuilder);
                        break;
                    }
                    Thread.sleep(3000);
                }
                stationModel.setState(State.SORTED);
                eventBuilder.id("2").data("Done sorting").build();
                emitter.send(eventBuilder);
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    @GetMapping("/restart")
    public String restartSystem() {
        stationModel.setErrorId(0);
        stationModel.setState(State.WAITING);
        stationModel.getThreadListener().start();
        return "redirect:/";
    }
}