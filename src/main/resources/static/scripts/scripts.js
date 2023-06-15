document.querySelector(".modal-click").disabled = true;

//ORDER OF WAGONS
function showValues() {
    var str = $( "form" ).serializeArray();
    var order = "";
    var res = "order=";
    str.forEach(function(elem) {
        order += elem.value;
    });
    res += order.split('').reverse().join('');
    console.log(res);
    return res
}
$( "select" ).on( "change", showValues );



//SEND TO STARTING POSITION
let cities = ["Москва", "Казань", "Магадан", "Воркута", "Якутск", "Тюмень"];
var toSortCounter = parseInt($("#toSortCounter").text(), 10);
$("#waitButton").click(function (e) {
    e.preventDefault();
    $(".state").text("Состояние: Прибытие");
    document.querySelector("#waitButton").style.display = "none";
    document.querySelector("#overlay").style.display = "block";

    if (window.EventSource == null) {
        alert('The browser does not support Server-Sent Events');
    } else {
        var eventSource = new EventSource('/wait');
        eventSource.onopen = function () {
            console.log('connection is established');
        };
        eventSource.onerror = function (error) {
            console.log('connection state: ' + eventSource.readyState + ', error: ' + error);
        };
        eventSource.onmessage = function (event) {
            console.log('id: ' + event.lastEventId + ', data: ' + event.data);
            switch (event.lastEventId ) {
                case "1":
                    toSortCounter+=1;
                    $("#toSortCounter").text(toSortCounter);
                    let template = " " +
                        "                    <div class=\"wagonItem\" id=\"wagonItem" + toSortCounter + "\">\n" +
                        "                        <div class=\"wagonNumber\">\n" +
                        "                            <p>" + toSortCounter + "</p>\n" +
                        "                        </div>\n" +
                        "                        <select class=\"selectWagon\" name=\"wagon\">\n" +
                        "                            <option value=\"" + cities.indexOf(event.data) + "\">" + event.data + "</option>\n"
                    cities.forEach(element => {
                        if(element !== event.data)
                            template += "                            <option value=\"" + cities.indexOf(element) + "\">" + element + "</option>\n";
                    })
                    template += "                        </select>\n" +
                        "\n" +
                        "                        <input type=\"checkbox\" class=\"type\"/>\n" +
                        "                    </div>";
                    document.querySelector(".wagonItems").innerHTML += template;
                    break;

                case "2":
                    $(".state").text("Состояние: Готово к сортировке");
                    document.querySelector("#startButton").style.display = "block";
                    document.querySelector("#overlay").style.display = "none"
                    eventSource.close();
                    break;

                case "3":
                    if(event.data < 5) {
                        $("#controllerError").text("Ошибка в работе контроллера " + event.data);
                    }
                    else {
                        $("#controllerError").text("Ошибка в подключении контроллера " + event.data-4);
                    }

                    $(".modal-click").modal({fadeDuration: 250});
                    eventSource.close();
                    document.querySelector("#restartButton").style.display = "block";
                    break;
            }
        };
    }
})

//START SORT
$("#startButton").click(function(e) {
    document.querySelector("#startButton").style.display = "none";
    document.querySelector("#overlay").style.display = "block";
    e.preventDefault();
    order = showValues();
    var str = $( "form" ).serializeArray();
    var order = "order=";
    str.forEach(function(elem) {
        order += elem.value;
    });

    $(".state").text("Состояние: Сортировка");
    var wagonList = document.querySelectorAll(".selectWagon");
    for (let el of wagonList) {el.disabled = true}


    if (window.EventSource == null) {
        alert('The browser does not support Server-Sent Events');
    } else {
        console.log(order);
        var eventSource = new EventSource('/start?' + order);
        eventSource.onopen = function () {
            console.log('connection is established');
        };
        eventSource.onerror = function (error) {
            console.log('connection state: ' + eventSource.readyState + ', error: ' + error);
        };
        eventSource.onmessage = function (event) {
            console.log('id: ' + event.lastEventId + ', data: ' + event.data);
            switch (event.lastEventId) {
                case "1":
                    document.querySelector(".wagonItems").removeChild(document.querySelector("#wagonItem" + toSortCounter));
                    toSortCounter -= 1;
                    $("#toSortCounter").text(toSortCounter);
                    $(".map").attr("src", "../images/Map_" + event.data + ".png");
                    var cityToAddCounter = $("#" + cities[parseInt(event.data, 10) - 1]);
                    cityToAddCounter.text(parseInt(cityToAddCounter.text(), 10) + 1);
                    break;
                case "2":
                    $(".state").text("Состояние: Отсортировано");
                    for (let el of wagonList) {el.disabled = false;}
                    document.querySelector("#restartButton").style.display = "block";
                    eventSource.close();
                    console.log('connection is closed');
                    break;
                case "3":
                    if(event.data < 5) {
                        $("#controllerError").text("Ошибка в работе контроллера " + event.data);
                    }
                    else {
                        $("#controllerError").text("Ошибка в подключении контроллера " + event.data-4);
                    }

                    $(".modal-click").modal({fadeDuration: 250});
                    eventSource.close();
                    document.querySelector("#restartButton").style.display = "block";
                    break;
            }
        };
    }
})

$("#restartButton").click(function(e) {
    document.querySelector(".wagonItems").innerHTML = "";
    $("#toSortCounter").text(0);
    cities.forEach(element => {
        $("#" + element).text(0);
    })
})

$("#alarmButton").click(function(e) {
    $(".modal-click").modal({fadeDuration: 250});
    document.querySelector("#waitButton").style.display = "none";
    document.querySelector("#startButton").style.display = "none";
    document.querySelector("#restartButton").style.display = "block";
})