package com.raspberry.pi4led.controllers;

import com.raspberry.pi4led.models.Control;
import com.raspberry.pi4led.models.State;
import com.raspberry.pi4led.models.StationModel;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Controller
public class WebController {
    private final ExecutorService cachedThreadPool = Executors.newCachedThreadPool();
    final StationModel stationModel = new StationModel(State.WAITING, Control.FIELD, "Сургутская");

    @GetMapping("/")
    public String greeting(Model model) throws InterruptedException {
        ArrayList<StationModel> station = new ArrayList<StationModel>();
        station.add(stationModel);
        model.addAttribute("station", station);
        model.addAttribute("cities", stationModel.getCities());
        model.addAttribute("counters", stationModel.getCounters());
        model.addAttribute("wagonList", stationModel.getWagonList());
        return "index";
    }

    @ResponseBody
    @GetMapping("/wait")
    public SseEmitter startSorting()  {
        SseEmitter emitter = new SseEmitter();
        if(stationModel.getState() == State.WAITING) {
            stationModel.setState(State.COMING);
            cachedThreadPool.execute(() -> {
                try {
                    stationModel.sendMessage(15); //moving to position for sorting
                    while (stationModel.convertReceived(stationModel.getReceivedMessage()) != 81) {
                        if (stationModel.convertReceived(stationModel.getReceivedMessage()) == 79) {
                            var eventBuilder = SseEmitter.event();
                            //stationModel.setTrainCounter(stationModel.getTrainCounter()+1);
                            //wagonModel newWagon = new wagonModel(stationModel.getTrainCounter()+1, stationModel.getCities().get(0), 0);
                            //stationModel.getWagonList().add(newWagon);
                            eventBuilder.id("1").data(stationModel.getCities().get(0));
                            emitter.send(eventBuilder);
                        }
                    }
                    var eventBuilder = SseEmitter.event();
                    stationModel.setState(State.READY);
                    eventBuilder.id("2").data("Ready to sort").build();
                    emitter.send(eventBuilder);

                    if(stationModel.getErrorId() != 0) {
                        eventBuilder = SseEmitter.event();
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

    @GetMapping(path = "/start", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter getWords(@RequestParam(value = "order", defaultValue = "0") String order) {
        stationModel.setState(State.SORTING);
        SseEmitter emitter = new SseEmitter();

        cachedThreadPool.execute(() -> {
            try {
                for(char way : order.toCharArray()) {
                    var eventBuilder = SseEmitter.event();
                    stationModel.setCurrentWay(Character.getNumericValue(way)+1);

                    int msgToSemaphore = 33 + (2 * stationModel.getCurrentWay());
                    stationModel.sendMessage(msgToSemaphore); //message to change semaphores

                    int msgToArrows = 1 + (2 * stationModel.getCurrentWay());
                    stationModel.sendMessage(msgToArrows); //message to change arrows

                    eventBuilder.id("1").data("Map_" + stationModel.getCurrentWay()).build();
                    emitter.send(eventBuilder);
                    if (stationModel.getErrorId() != 0) {
                        eventBuilder.id("3").data(stationModel.getErrorId()); //to open modal with error
                        emitter.send(eventBuilder);
                        break;
                    }
                    int msgToReceive = 65+(2 * stationModel.getCurrentWay());
                    while(stationModel.convertReceived(stationModel.getReceivedMessage())!=msgToReceive) {
                        Thread.onSpinWait();
                    }
//                    stationModel.getCounters().set(stationModel.getCurrentWay()-1,
//                            stationModel.getCounters().get(stationModel.getCurrentWay()-1) + 1);
                }
                var eventBuilder = SseEmitter.event();
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
        stationModel.setTrainCounter(0);
        stationModel.getWagonList().clear();
        stationModel.getThreadListener().start();
        for (int i =0; i!= stationModel.getCounters().size(); i++) {
            stationModel.getCounters().set(i, 0);
        }
        stationModel.setCurrentWay(8);
        return "redirect:/";
    }
}