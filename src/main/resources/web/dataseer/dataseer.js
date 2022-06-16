/**
 *  Javascript functions for the front end.
 *
 *  Author: Patrice Lopez
 */

var grobid = (function ($) {
    var supportedLanguages = ["en"];

    // for components view
    var entities = null;

    // for complete Wikidata concept information, resulting of additional calls to the knowledge base service
    var conceptMap = new Object();

    // store the current entities extracted by the service
    var entityMap = new Object();

    // store the references attached to the entities and extracted by the service
    var referenceMap = new Object();

    function defineBaseURL(ext) {
        var baseUrl = null;
        var localBase = $(location).attr('href');
        ext = "service/" + ext;
        if (localBase.indexOf("index.html") != -1) {
            baseUrl = localBase.replace("index.html", ext);
        } else if (localBase.endsWith("#")) {
            baseUrl = localBase.substring(0,localBase.length-1) + ext;
        }
        else
            baseUrl = $(location).attr('href') + ext;
        return baseUrl;
    }

    function setBaseUrl(ext) {
        var baseUrl = defineBaseURL(ext);
        $('#gbdForm').attr('action', baseUrl);
    }

    function setBaseUrl2(ext) {
        var baseUrl = defineBaseURL(ext);
        $('#gbdForm2').attr('action', baseUrl);
    }

    $(document).ready(function () {

        $("#subTitle").html("About");
        $("#divAbout").show();
        $("#divRestI").hide();
        $("#divRestII").hide();
        $("#divDoc").hide();

        createInputTextArea();

        $("#selectedService").val('annotateDatasetSentence');
        $('#selectedService').change(function () {
            processChange();
            return true;
        });

        $("#selectedService2").val('processDataseerSentence');
        $('#selectedService2').change(function () {
            processChange2();
            return true;
        });

        $('#submitRequest').bind('click', submitQuery);
        $('#submitRequest2').bind('click', submitQuery2);

        setBaseUrl('annotateDatasetSentence');
        setBaseUrl2('annotateDataseerSentence');
        setExamples('1');
        setExamples('2');

        $("#about").click(function () {
            $("#about").attr('class', 'section-active');
            $("#rest").attr('class', 'section-not-active');
            $("#rest2").attr('class', 'section-not-active');
            $("#doc").attr('class', 'section-not-active');
            $("#demo").attr('class', 'section-not-active');

            $("#subTitle").html("About");
            $("#subTitle").show();

            $("#divAbout").show();
            $("#divRestI").hide();
            $("#divRestII").hide();
            $("#divDoc").hide();
            $("#divDemo").hide();
            return false;
        });
        $("#rest").click(function () {
            $("#rest").attr('class', 'section-active');
            $("#rest2").attr('class', 'section-not-active');
            $("#doc").attr('class', 'section-not-active');
            $("#about").attr('class', 'section-not-active');
            $("#demo").attr('class', 'section-not-active');

            $("#subTitle").hide();
            //$("#subTitle").show();
            processChange();

            $("#divRestI").show();
            $("#divRestII").hide();
            $("#divAbout").hide();
            $("#divDoc").hide();
            $("#divDemo").hide();
            return false;
        });
        $("#rest2").click(function () {
            $("#rest").attr('class', 'section-not-active');
            $("#rest2").attr('class', 'section-active');
            $("#doc").attr('class', 'section-not-active');
            $("#about").attr('class', 'section-not-active');
            $("#demo").attr('class', 'section-not-active');

            $("#subTitle2").hide();
            //$("#subTitle").show();
            processChange2();

            $("#divRestI").hide();
            $("#divRestII").show();
            $("#divAbout").hide();
            $("#divDoc").hide();
            $("#divDemo").hide();
            return false;
        });
        $("#doc").click(function () {
            $("#doc").attr('class', 'section-active');
            $("#rest").attr('class', 'section-not-active');
            $("#rest2").attr('class', 'section-not-active');
            $("#about").attr('class', 'section-not-active');
            $("#demo").attr('class', 'section-not-active');

            $("#subTitle").html("Doc");
            $("#subTitle").show();

            $("#divDoc").show();
            $("#divAbout").hide();
            $("#divRestI").hide();
            $("#divRestII").hide();
            $("#divDemo").hide();
            return false;
        });
    });

    function ShowRequest(formData, jqForm, options) {
        var queryString = $.param(formData);
        $('#infoResult').html('<font color="red">Requesting server...</font>');
        return true;
    }

    function ShowRequest2(formData, jqForm, options) {
        var queryString = $.param(formData);
        $('#infoResult2').html('<font color="red">Requesting server...</font>');
        return true;
    }

    function AjaxError(jqXHR, textStatus, errorThrown) {
        $('#infoResult').html("<font color='red'>Error encountered while requesting the server.<br/>" + jqXHR.responseText + "</font>");
        entities = null;
    }

    function AjaxError2(jqXHR, textStatus, errorThrown) {
        $('#infoResult2').html("<font color='red'>Error encountered while requesting the server.<br/>" + jqXHR.responseText + "</font>");
        entities = null;
    }

    function AjaxError3(message) {
        if (!message)
            message = "";
        message += " - The PDF document cannot be annotated. Please check the server logs.";
        $('#infoResult').html("<font color='red'>Error encountered while requesting the server.<br/>"+message+"</font>");
        entities = null;
        return true;
    }

    function htmll(s) {
        return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    function submitQuery() {
        $('#infoResult').html('<font color="grey">Requesting server...</font>');
        $('#requestResult').html('');

        // re-init the entity map
        entityMap = new Object();
        conceptMap = new Object();
        referenceMap = new Object();

        var selected = $('#selectedService option:selected').attr('value');
        var urlLocal = $('#gbdForm').attr('action');
        if (selected == 'annotateDatasetSentence') {
            {
                $.ajax({
                    type: 'GET',
                    url: urlLocal,
                    data: {text: $('#inputTextArea1').val()},
                    success: SubmitSuccesful,
                    error: AjaxError,
                    contentType: false
                    //dataType: "text"
                });
            }
        }
        else if (selected == 'processDatasetTEI' || selected == 'processDatasetJATS') {
            var form = document.getElementById('gbdForm');
            var formData = new FormData(form);
            var xhr = new XMLHttpRequest();
            var url = urlLocal
            xhr.responseType = 'xml'; 
            xhr.open('POST', url, true);

            xhr.onreadystatechange = function (e) {
                if (xhr.readyState == 4 && xhr.status == 200) {
                    var response = e.target.response;
                    //console.log(response);
                    SubmitSuccesful(response, xhr.status);
                } else if (xhr.status != 200) {
                    AjaxError("Response " + xhr.status + ": ");
                }
            };
            xhr.send(formData);
        }
        else if (selected == 'annotateDatasetPDF') {
            // we will have JSON annotations to be layered on the PDF

            // request for the annotation information
            var form = document.getElementById('gbdForm');
            var formData = new FormData(form);
            var xhr = new XMLHttpRequest();
            var url = $('#gbdForm').attr('action');
            xhr.responseType = 'json'; 
            xhr.open('POST', url, true);
            //ShowRequest2();

            var nbPages = -1;

            // display the local PDF
            if ((document.getElementById("input").files[0].type == 'application/pdf') ||
                (document.getElementById("input").files[0].name.endsWith(".pdf")) ||
                (document.getElementById("input").files[0].name.endsWith(".PDF")))
                var reader = new FileReader();
            reader.onloadend = function () {
                // to avoid cross origin issue
                //PDFJS.disableWorker = true;
                var pdfAsArray = new Uint8Array(reader.result);
                // Use PDFJS to render a pdfDocument from pdf array
                PDFJS.getDocument(pdfAsArray).then(function (pdf) {
                    // Get div#container and cache it for later use
                    var container = document.getElementById("requestResult");
                    // enable hyperlinks within PDF files.
                    //var pdfLinkService = new PDFJS.PDFLinkService();
                    //pdfLinkService.setDocument(pdf, null);

                    //$('#requestResult').html('');
                    nbPages = pdf.numPages;

                    // Loop from 1 to total_number_of_pages in PDF document
                    for (var i = 1; i <= nbPages; i++) {

                        // Get desired page
                        pdf.getPage(i).then(function (page) {
                            var table = document.createElement("table");
                            table.setAttribute('style', 'table-layout: fixed; width: 100%;')
                            var tr = document.createElement("tr");
                            var td1 = document.createElement("td");
                            var td2 = document.createElement("td");

                            tr.appendChild(td1);
                            tr.appendChild(td2);
                            table.appendChild(tr);

                            var div0 = document.createElement("div");
                            div0.setAttribute("style", "text-align: center; margin-top: 1cm;");
                            var pageInfo = document.createElement("p");
                            var t = document.createTextNode("page " + (page.pageIndex + 1) + "/" + (nbPages));
                            pageInfo.appendChild(t);
                            div0.appendChild(pageInfo);

                            td1.appendChild(div0);


                            var div = document.createElement("div");

                            // Set id attribute with page-#{pdf_page_number} format
                            div.setAttribute("id", "page-" + (page.pageIndex + 1));

                            // This will keep positions of child elements as per our needs, and add a light border
                            div.setAttribute("style", "position: relative; ");

                            // Create a new Canvas element
                            var canvas = document.createElement("canvas");
                            canvas.setAttribute("style", "border-style: solid; border-width: 1px; border-color: gray;");

                            // Append Canvas within div#page-#{pdf_page_number}
                            div.appendChild(canvas);

                            // Append div within div#container
                            td1.setAttribute('style', 'width:70%;');
                            td1.appendChild(div);

                            var annot = document.createElement("div");
                            annot.setAttribute('style', 'vertical-align:top;');
                            annot.setAttribute('id', 'detailed_annot-' + (page.pageIndex + 1));
                            td2.setAttribute('style', 'vertical-align:top;width:30%;');
                            td2.appendChild(annot);

                            container.appendChild(table);

                            //fitToContainer(canvas);

                            // we could think about a dynamic way to set the scale based on the available parent width
                            //var scale = 1.2;
                            //var viewport = page.getViewport(scale);
                            var viewport = page.getViewport((td1.offsetWidth * 0.98) / page.getViewport(1.0).width);

                            var context = canvas.getContext('2d');
                            canvas.height = viewport.height;
                            canvas.width = viewport.width;

                            var renderContext = {
                                canvasContext: context,
                                viewport: viewport
                            };

                            // Render PDF page
                            page.render(renderContext).then(function () {
                                // Get text-fragments
                                return page.getTextContent();
                            })
                            .then(function (textContent) {
                                // Create div which will hold text-fragments
                                var textLayerDiv = document.createElement("div");

                                // Set it's class to textLayer which have required CSS styles
                                textLayerDiv.setAttribute("class", "textLayer");

                                // Append newly created div in `div#page-#{pdf_page_number}`
                                div.appendChild(textLayerDiv);

                                // Create new instance of TextLayerBuilder class
                                var textLayer = new TextLayerBuilder({
                                    textLayerDiv: textLayerDiv,
                                    pageIndex: page.pageIndex,
                                    viewport: viewport
                                });

                                // Set text-fragments
                                textLayer.setTextContent(textContent);

                                // Render text-fragments
                                textLayer.render();
                            });
                        });
                    }
                });
            }
            reader.readAsArrayBuffer(document.getElementById("input").files[0]);

            xhr.onreadystatechange = function (e) {
                if (xhr.readyState == 4 && xhr.status == 200) {
                    var response = e.target.response;
                    //var response = JSON.parse(xhr.responseText);
                    //console.log(response);
                    setupAnnotations(response);
                } else if (xhr.status != 200) {
                    AjaxError3("Response " + xhr.status + ": ");
                }
            };
            xhr.send(formData);
        }
    }

    function submitQuery2() {
        $('#infoResult2').html('<font color="grey">Requesting server...</font>');
        $('#requestResult2').html('');

        // re-init the entity map
        entityMap = new Object();
        conceptMap = new Object();

        var selected = $('#selectedService2 option:selected').attr('value');
        var urlLocal = $('#gbdForm2').attr('action');
        if (selected == 'processDataseerSentence') {
            {
                $.ajax({
                    type: 'GET',
                    url: urlLocal,
                    data: {text: $('#inputTextArea2').val()},
                    success: SubmitSuccesful2,
                    error: AjaxError,
                    contentType: false
                    //dataType: "text"
                });
            }
        }
        else if (selected == 'processDataseerTEI' || selected == 'processDataseerJATS' || selected == 'processDataseerPDF') {
            var form = document.getElementById('gbdForm2');
            var formData = new FormData(form);
            var xhr = new XMLHttpRequest();
            var url = urlLocal
            xhr.responseType = 'xml'; 
            xhr.open('POST', url, true);

            xhr.onreadystatechange = function (e) {
                if (xhr.readyState == 4 && xhr.status == 200) {
                    var response = e.target.response;
                    //console.log(response);
                    SubmitSuccesful2(response, xhr.status);
                } else if (xhr.status != 200) {
                    AjaxError("Response " + xhr.status + ": ");
                }
            };
            xhr.send(formData);
        }
        
    }

    function SubmitSuccesful(responseText, statusText) {
        var selected = $('#selectedService option:selected').attr('value');

        if (selected == 'annotateDatasetSentence') {
            SubmitSuccesfulText(responseText, statusText);
        } else if (selected == 'annotateDatasetPDF') {
            //SubmitSuccesfulXML(responseText, statusText);
        } else if (selected == 'processDatasetTEI') {
            //SubmitSuccesfulXML(responseText, statusText);
        } else if (selected == 'processDatasetJATS') {
            //SubmitSuccesfulXML(responseText, statusText);
        } 
    }

    function SubmitSuccesful2(responseText, statusText) {
        var selected = $('#selectedService2 option:selected').attr('value');

        if (selected == 'processDataseerSentence') {
            SubmitSuccesfulText2(responseText, statusText);
        } else if (selected == 'processDataseerPDF') {
            SubmitSuccesfulXML(responseText, statusText);
        } else if (selected == 'processDataseerTEI') {
            SubmitSuccesfulXML(responseText, statusText);
        } else if (selected == 'processDataseerJATS') {
            SubmitSuccesfulXML(responseText, statusText);
        } 
    }

    function SubmitSuccesfulText2(responseText, statusText) {
        responseJson = responseText;
        if ((responseJson == null) || (responseJson.length == 0)) {
            $('#infoResult2')
                .html("<font color='red'>Error encountered while receiving the server's answer: response is empty.</font>");
            return;
        } else {
            $('#infoResult2').html('');
        }

        responseJson = jQuery.parseJSON(responseJson);

        var display = '<div class=\"note-tabs\"> \
            <ul id=\"resultTab\" class=\"nav nav-tabs\"> \
                <li class="active"><a href=\"#navbar-fixed-json\" data-toggle=\"tab\">Response</a></li> \
            </ul> \
            <div class="tab-content"> \
            <div class="tab-pane active" id="navbar-fixed-annotation">\n';

        display += '<div class="tab-pane " id="navbar-fixed-json">\n';
        display += "<pre class='prettyprint' id='jsonCode'>";
        display += "<pre class='prettyprint lang-json' id='xmlCode'>";
        var testStr = vkbeautify.json(responseText);

        display += htmll(testStr);

        display += "</pre>";
        display += '</div></div></div>';

        $('#requestResult2').html(display);
        window.prettyPrint && prettyPrint();

        $('#requestResult2').show();
    }

    function SubmitSuccesfulText(responseText, statusText) {
        responseJson = responseText;
        if ((responseJson == null) || (responseJson.length == 0)) {
            $('#infoResult')
                .html("<font color='red'>Error encountered while receiving the server's answer: response is empty.</font>");
            return;
        } else {
            $('#infoResult').html('');
        }

        responseJson = jQuery.parseJSON(responseJson);

        var display = '<div class=\"note-tabs\"> \
            <ul id=\"resultTab\" class=\"nav nav-tabs\"> \
                <li class="active"><a href=\"#navbar-fixed-annotation\" data-toggle=\"tab\">Annotations</a></li> \
                <li><a href=\"#navbar-fixed-json\" data-toggle=\"tab\">Response</a></li> \
            </ul> \
            <div class="tab-content"> \
            <div class="tab-pane active" id="navbar-fixed-annotation">\n';

        display += '<pre style="background-color:#FFF;width:95%;" id="displayAnnotatedText">';

        var string = $('#inputTextArea1').val();
        var newString = "";
        var lastMaxIndex = string.length;

        display += '<table id="sentence" style="width:100%;table-layout:fixed;" class="table">';
        //var string = responseJson.text;

        display += '<tr style="background-color:#FFF;">';
        entities = responseJson.mentions;
        var lang = 'en';
        if (responseJson.lang)   
            lang = responseJson.lang;

        if (entities) {
            var pos = 0; // current position in the text

            for (var currentEntityIndex = 0; currentEntityIndex < entities.length; currentEntityIndex++) {

                var entity = entities[currentEntityIndex];
                var identifier = entity.wikipediaExternalRef;
                var wikidataId = entity.wikidataId;
                
                var localLang = lang
                if (entity.lang)
                    localLang = entity.lang;

                if (identifier && (conceptMap[identifier] == null)) {
                    fetchConcept(identifier, localLang, function (result) {
                        conceptMap[result.wikipediaExternalRef] = result;
                    });
                }

                var pieces = []

                var datasetName = entity['dataset-name'];
                if (datasetName) {
                    datasetName['subtype'] = 'dataset-name';
                    pieces.push(datasetName);
                }

                var datasetImplicit = entity['dataset-implicit'];
                if (datasetImplicit) {
                    datasetImplicit['subtype'] = 'dataset-implicit';
                    pieces.push(datasetImplicit);
                }

                var dataDevice = entity['data-device'];
                if (dataDevice) {
                    dataDevice['subtype'] = 'data-device';
                    pieces.push(dataDevice);
                }

                var url = entity['url']
                if (url) {
                    url['subtype'] = 'url'
                    pieces.push(url)
                }

                var publisher = entity['publisher']
                if (publisher) {
                    publisher['subtype'] = 'publisher'
                    pieces.push(publisher)
                }

                var references = entity['references']
                if (references) {
                    for(var reference in references) {
                        reference['subtype'] = 'reference';
                        if (!reference['rawForm'])
                            reference['rawForm'] = reference['label']
                        pieces.push(reference)    
                    }
                }
            
                pieces.sort(function(a, b) { 
                    var startA = parseInt(a.offsetStart, 10);
                    //var endA = parseInt(a.offsetEnd, 10);

                    var startB = parseInt(b.offsetStart, 10);
                    //var endB = parseInt(b.offsetEnd, 10);

                    return startA-startB; 
                });

                for (var pi in pieces) {
                    piece = pieces[pi]

                    var entityRawForm = piece.rawForm;
                    var start = parseInt(piece.offsetStart, 10);
                    var end = parseInt(piece.offsetEnd, 10);
        
                    if (start < pos) {
                        // we have a problem in the initial sort of the entities
                        // the server response is not compatible with the present client 
                        console.log("Sorting of entities as present in the server's response not valid for this client.");
                        // note: this should never happen
                    } else {
                        newString += string.substring(pos, start)
                            //+ '<span id="annot-' + currentEntityIndex + '" rel="popover" data-color="' + piece['subtype'] + '">'
                            //+ '<span id="annot-' + currentEntityIndex + '-' + pi + '">'
                            //+ '<span class="label ' + piece['subtype'] + '" style="cursor:hand;cursor:pointer;" >'
                            + '<span id="annot-' + currentEntityIndex + '-' + pi + '" class="label ' + piece['subtype'] + '" style="cursor:hand;cursor:pointer;" >'
                            + string.substring(start, end) + '</span>';
                        pos = end;
                    }
                }
            }
            newString += string.substring(pos, string.length);
        }

        newString = "<p>" + newString.replace(/(\r\n|\n|\r)/gm, "</p><p>") + "</p>";
        //string = string.replace("<p></p>", "");

        display += '<td style="font-size:small;width:60%;border:1px solid #CCC;"><p>' + newString + '</p></td>';
        display += '<td style="font-size:small;width:40%;padding:0 5px; border:0"><span id="detailed_annot-0" /></td>';
        display += '</tr>';
        display += '</table>\n';
        display += '</pre>\n';
        display += '</div> \
                    <div class="tab-pane " id="navbar-fixed-json">\n';
        display += "<pre class='prettyprint' id='jsonCode'>";
        display += "<pre class='prettyprint lang-json' id='xmlCode'>";
        var testStr = vkbeautify.json(responseText);

        display += htmll(testStr);

        display += "</pre>";
        display += '</div></div></div>';

        $('#requestResult').html(display);
        window.prettyPrint && prettyPrint();

        if (entities) {
            for (var entityIndex = 0; entityIndex < entities.length; entityIndex++) {
                var entity = entities[entityIndex];

                var indexComp = 0;
                if (entity['dataset-name'])
                    indexComp++;
                if (entity['dataset-implicit'])
                    indexComp++;
                if (entity['data-device'])
                    indexComp++;
                if (entity['url'])
                    indexComp++;
                if (entity['publisher'])
                    indexComp++;
                for(var currentIndexComp = 0; currentIndexComp< indexComp; currentIndexComp++) {
                    $('#annot-' + entityIndex + '-' + currentIndexComp).bind('mouseenter', viewEntity);
                    $('#annot-' + entityIndex + '-' + currentIndexComp).bind('click', viewEntity);
                }
            }
        }

        $('#detailed_annot-0').hide();

        $('#requestResult').show();
    }

    function SubmitSuccesfulXML(responseText, statusText) {
        responseXML = responseText;
        if ((responseXML == null) || (responseXML.length == 0)) {
            $('#infoResult2')
                .html("<font color='red'>Error encountered while receiving the server's answer: response is empty.</font>");
            return;
        } else {
            $('#infoResult2').html('');
        }

        var display = '<div class=\"note-tabs\"> \
            <ul id=\"resultTab\" class=\"nav nav-tabs\"> \
                <li class="active"><a href=\"#navbar-fixed-xml\" data-toggle=\"tab\">Response</a></li> \
            </ul> \
            <div class="tab-content"> \
            <div class="tab-pane active" id="navbar-fixed-annotation">\n';

        
        display += '<div class="tab-pane " id="navbar-fixed-xml">\n';
        display += "<pre class='prettyprint' id='xmlCode'>";
        display += "<pre class='prettyprint lang-xml' id='xmlCode'>";
        var testStr = vkbeautify.xml(responseXML);

        display += htmll(testStr);

        display += "</pre>";
        display += '</div></div></div>';

        $('#requestResult2').html(display);
        window.prettyPrint && prettyPrint();

        $('#requestResult2').show();
    }

    function fetchConcept(identifier, lang, successFunction) {
        $.ajax({
            type: 'GET',
            url: 'https://cloud.science-miner.com/nerd/service/kb/concept/' + identifier + '?lang=' + lang,
            success: successFunction,
            dataType: 'json'
        });
    }

    function setupAnnotations(response) {
        // we must check/wait that the corresponding PDF page is rendered at this point
        if ((response == null) || (response.length == 0)) {
            $('#infoResult')
                .html("<font color='red'>Error encountered while receiving the server's answer: response is empty.</font>");
            return;
        } else {
            // we will print an index summarizing the result here
            $('#infoResult').empty();
            $('#infoResult').hide();
        }

        var json = response;
        var pageInfo = json.pages;

        var page_height = 0.0;
        var page_width = 0.0;

        entities = json.mentions;
        if (entities) {
            // hey bro, this must be asynchronous to avoid blocking the brothers
            entities.forEach(function (entity, n) {
                entityMap[n] = [];
                entityMap[n].push(entity);

                var identifier = entity.wikipediaExternalRef;
                var wikidataId = entity.wikidataId;
                
                var localLang = lang
                if (entity.lang)
                    localLang = entity.lang;

                if (identifier && (conceptMap[identifier] == null)) {
                    fetchConcept(identifier, localLang, function (result) {
                        conceptMap[result.wikipediaExternalRef] = result;
                    });
                }

                //console.log(entity)
                var pieces = []

                if (entity['dataset-name']) {
                    var datasetName = entity['dataset-name'];
                    datasetName['subtype'] = 'dataset-name'
                    pieces.push(datasetName);
                }

                if (entity['dataset-implicit']) {
                    var dataset = entity['dataset-implicit'];
                    dataset['subtype'] = 'dataset-implicit'
                    pieces.push(dataset);
                }

                if (entity['data-device']) {
                    var dataDevice = entity['data-device'];
                    dataDevice['subtype'] = 'data-device';
                    pieces.push(dataDevice);
                }

                if (entity['url']) {
                    var url = entity['url'];
                    url['subtype'] = 'url';
                    pieces.push(url)
                }

                if (entity['publisher']) {
                    var creator = entity['publisher']
                    creator['subtype'] = 'publisher'
                    pieces.push(creator)
                }

                var references = entity['references']
                if (references) {
                    for(var r in references) {
                        references[r]['subtype'] = 'reference';
                        if (!references[r]['rawForm']) {
                            references[r]['rawForm'] = references[r]['label']
                        }
                        pieces.push(references[r])    
                    }
                }

                pieces.sort(function(a, b) { 
                    var startA = parseInt(a.offsetStart, 10);
                    //var endA = parseInt(a.offsetEnd, 10);

                    var startB = parseInt(b.offsetStart, 10);
                    //var endB = parseInt(b.offsetEnd, 10);

                    return startA-startB; 
                });

                var type = entity['type']
                var id = entity['id']
                for (var pi in pieces) {
                    piece = pieces[pi]
                    //console.log(piece)
                    var pos = piece.boundingBoxes;
                    var rawForm = piece.rawForm
                    if ((pos != null) && (pos.length > 0)) {
                        pos.forEach(function(thePos, m) {
                            // get page information for the annotation
                            var pageNumber = thePos.p;
                            if (pageInfo[pageNumber-1]) {
                                page_height = pageInfo[pageNumber-1].page_height;
                                page_width = pageInfo[pageNumber-1].page_width;
                            }
                            annotateEntity(id, rawForm, piece["subtype"], thePos, page_height, page_width, n, pi+m);
                        });
                    }
                }
            });
        }

        var references = json.references
        if (references) {
            references.forEach(function (reference, n) {
                referenceMap[reference.refKey] = reference.tei;
            });
        }

        displaySummary(response)
        $('#infoResult').show();
    }

    function displaySummary(response) {

        $('#infoResult').empty();
        entities = response.mentions;
        // get page canvs width for visual alignment
        if ($("canvas").length > 0)
            width = $("canvas").first().width();
        else 
            width = "70%";
        if (entities) {
            var summary = '';
            summary += "<div id='mention-count' style='background-color: white; width: 100%;'><p>&nbsp;&nbsp;<b>"+ entities.length + "</b> dataset mentions found</p></div>";

            summary += "<table width='" + width+ "px' style='table-layout: fixed; overflow: scroll; padding-left:5px; width:"+width+"%;'>";
            summary += '<colgroup><col span="1" style="width: 10%;"><col span="1" style="width: 25%;"><col span="1" style="width: 5%;"><col span="1" style="width: 40%;"><col span="1" style="width: 20%;"></colgroup>';

            var local_map = new Map();
            var usage_map = new Map();
            var type_map = new Map();

            entities.forEach(function (entity, n) {
                var local_page = -1;
                if (entity['dataset-name'] && entity['dataset-name'].boundingBoxes && entity['dataset-name'].boundingBoxes.length>0)
                    local_page = entity['dataset-name'].boundingBoxes[0].p;
                else if (entity['dataset-implicit'] && entity['dataset-implicit'].boundingBoxes && entity['dataset-implicit'].boundingBoxes.length>0)
                    local_page = entity['dataset-implicit'].boundingBoxes[0].p;

                var the_id = 'annot-' + n + '-00';
                if (local_page != -1)
                    the_id += '_' + local_page;

                var datasetNameRaw;
                if (entity['dataset-name']) 
                    datasetNameRaw = entity['dataset-name'].normalizedForm;
                else if (entity['dataset-implicit']) 
                    datasetNameRaw = entity['dataset-implicit'].normalizedForm;

                if (datasetNameRaw) {
                    if (!type_map.has(datasetNameRaw)) 
                        type_map[datasetNameRaw] = entity['type']

                    if (!local_map.has(datasetNameRaw)) 
                        local_map.set(datasetNameRaw, new Array());
                    
                    var localArray = local_map.get(datasetNameRaw)
                    localArray.push(the_id)
                    local_map.set(datasetNameRaw, localArray);

                    if (entity['documentContextAttributes']) {
                        //console.log(entity['documentContextAttributes']);
                        usage_map.set(datasetNameRaw, entity['documentContextAttributes']);
                    }
                }
            });

            var span_ids = new Array();

            var allTableContentNamed = "";
            var allTableContentImplicit = "";
            var n = 0;
            for (let [key, value] of local_map) {
                var tableContent = "";
                tableContent += "<tr width='"+width+"' style='background: ";
                if (n%2 == 0) {
                    tableContent += "#eee;'>"
                } else {
                    tableContent += "#fff;'>"
                }
                if (type_map[key]) {
                    if (type_map[key] === 'dataset-name')
                        tableContent += "<td>named</td>";
                    else if (type_map[key] === 'dataset-implicit')
                        tableContent += "<td>implicit</td>";
                } else
                    tableContent += "<td></td>";
                tableContent += "<td>"+key+"</td>";
                tableContent += "<td>"+value.length+"</td>";
                tableContent += "<td style='display: inline-block; word-break: break-word;' >";

                value.sort(function(a, b) {
                    var a_page = -1;
                    if (a.indexOf("_") != -1) {
                        var local_page = a.substring(a.indexOf("_")+1);
                        a_page = parseInt(local_page);
                        if (isNaN(a_page))
                            a_page = -1;
                    }

                    var b_page = -1;
                    if (b.indexOf("_") != -1) {
                        var local_page = b.substring(b.indexOf("_")+1);
                        b_page = parseInt(local_page);
                        if (isNaN(b_page))
                            b_page = -1;
                    }

                    if (a_page < b_page) {
                        return -1;
                    }
                    if (a_page > b_page) {
                        return 1;
                    }
                    return 0;
                });

                for (var i=0; i<value.length; i++) {
                    var the_id_full = value[i];
                    var the_id = the_id_full;
                    var local_page = the_id_full;
                    if (the_id_full.indexOf("_") != -1) {
                        the_id = the_id_full.substring(0,the_id_full.indexOf("_"));
                        local_page = the_id_full.substring(the_id_full.indexOf("_"));
                    }
                    tableContent += "<span class='index' id='index_"+the_id+"'>page"+local_page+"</span> ";
                    span_ids.push('index_'+the_id);
                }

                var attributesInfo = "";
                //console.log(key);
                if (usage_map.get(key)) {
                    documentAttributes = usage_map.get(key);
                    console.log(documentAttributes);
                    if (documentAttributes.used.value)
                        attributesInfo += "used";
                    if (documentAttributes.created.value)
                        attributesInfo += " created";
                    if (documentAttributes.shared.value)
                        attributesInfo += " shared";
                }

                tableContent += "<td>" + attributesInfo + "</td></tr>";

                if (type_map[key]) {
                    if (type_map[key] === 'dataset-name')
                        allTableContentNamed += tableContent;
                    else
                        allTableContentImplicit += tableContent;
                }
                n++;
            };

            summary += allTableContentNamed + allTableContentImplicit;
            summary += "</table>";

            summary += "</div>";

            $('#infoResult').html('<div style="width: '+width+
                    '; border-style: solid; border-width: 1px; border-color: gray; background-color: white;">' +
                    summary);

            $('#toggle-group').bigSlide( {side: 'right', menu: '#drawer', menuWidth: width, afterOpen() {
                //$("#pure-toggle-right").checked = false;
                $("#pure-toggle-right").bind('click', function (event) {
                    console.log("#pure-toggle-right click");
                    $("#toggle-group").click();
                });
                $('#drawer').show();

            }, 
            afterClose() {
                $('#drawer').hide();
            }
            });

            for(var span_index in span_ids) {
                summary = summary.replace(span_ids[span_index], span_ids[span_index]+"-drawer");
            }
            $('#drawer').html('<div style="width: '+width+
                '; border-style: solid; border-width: 1px; border-color: gray; background-color: white; margin: auto;">'+ 
                summary.replace('mention-count', 'mention-count-drawer'));

            //$("#toggle-group").click(); //slides the menu open
            $("#pure-toggle-right").show();
            $("#toggle-group").show();

            //menu.reset(); //removes all the css that sliiide added to any element

            for(var span_index in span_ids) {
                var span_id = span_ids[span_index];
                $("#"+span_ids[span_index]).bind('click', function (event) {
                    var localId = $(this).attr('id');

                    const local_target = document.querySelector('#'+localId.replace('index_',''));
                    const topPos = local_target.getBoundingClientRect().top + window.pageYOffset - 100;

                    window.scrollTo({
                      top: topPos, 
                      behavior: 'smooth' 
                    });

                    local_target.click();
                });

                $("#"+span_ids[span_index]+"-drawer").bind('click', function (event) {
                    var localId = $(this).attr('id');

                    const local_target = document.querySelector('#'+localId.replace('index_','').replace("-drawer",""));
                    const topPos = local_target.getBoundingClientRect().top + window.pageYOffset - 100;

                    window.scrollTo({
                      top: topPos, 
                      behavior: 'smooth' 
                    });

                    local_target.click();

                    $("#toggle-group").click();
                });
            }

            /*$("#pure-toggle-right").bind('click', function (event) {
                if (('#pure-toggle-right').checked) {
                    menu.deactivate(); 
                    $("#pure-toggle-right").checked = false;
                }
                else {
                    menu.activate();
                    $("#pure-toggle-right").checked = true;
                }
            });*/
        }
    }

    function annotateEntity(theId, rawForm, theType, thePos, page_height, page_width, entityIndex, positionIndex) {
        //console.log('annotate: ' + ' ' + rawForm + ' ' + theType + ' ')
        //console.log(thePos)

        var page = thePos.p;
        var pageDiv = $('#page-'+page);
        var canvas = pageDiv.children('canvas').eq(0);;

        var canvasHeight = canvas.height();
        var canvasWidth = canvas.width();
        var scale_y = canvasHeight / page_height;
        var scale_x = canvasWidth / page_width;

        /*console.log('canvasHeight: ' + canvasHeight);
        console.log('canvasWidth: ' + canvasWidth);
        console.log('page_height: ' + page_height);
        console.log('page_width: ' + page_width);
        console.log('scale_x: ' + scale_x);
        console.log('scale_y: ' + scale_y);*/

        var x = (thePos.x * scale_x) - 1;
        var y = (thePos.y * scale_y) - 1;
        var width = (thePos.w * scale_x) + 1;
        var height = (thePos.h * scale_y) + 1;

        //make clickable the area
        var element = document.createElement("a");
        var attributes = "display:block; width:"+width+"px; height:"+height+"px; position:absolute; top:"+
            y+"px; left:"+x+"px;";
        element.setAttribute("style", attributes + "border:2px solid;");
        
        // to have a pop-up window, uncomment
        //element.setAttribute("data-toggle", "popover");
        //element.setAttribute("data-placement", "top");
        //element.setAttribute("data-content", "content");
        //element.setAttribute("data-trigger", "hover");
        /*$(element).popover({
            content: "<p>Dataset Entity</p><p>" +rawForm+"<p>",
            html: true,
            container: 'body'
        });*/
        //console.log(element)

        element.setAttribute("class", theType.toLowerCase());
        element.setAttribute("id", 'annot-' + entityIndex + '-' + positionIndex);
        element.setAttribute("page", page);

        pageDiv.append(element);

        $('#annot-' + entityIndex + '-' + positionIndex).bind('mouseenter', viewEntityPDF);
        $('#annot-' + entityIndex + '-' + positionIndex).bind('click', viewEntityPDF);
    }

    function viewEntityPDF() {
        var pageIndex = $(this).attr('page');
        var localID = $(this).attr('id');

        //console.log('viewEntityPDF ' + pageIndex + ' / ' + localID);
        if (entities == null) {
            return;
        }

        var topPos = $(this).position().top;

        var ind1 = localID.indexOf('-');
        var localEntityNumber = parseInt(localID.substring(ind1 + 1, localID.length));

        if ((entityMap[localEntityNumber] == null) || (entityMap[localEntityNumber].length == 0)) {
            // this should never be the case
            console.log("Error for visualising annotation with id " + localEntityNumber
                + ", empty list of entities");
        }

        var lang = 'en'; //default
        var string = "";
        for (var entityListIndex = entityMap[localEntityNumber].length - 1;
                entityListIndex >= 0;
                entityListIndex--) {
            var entity = entityMap[localEntityNumber][entityListIndex];

            var localComponent = null;
            if (entity["dataset-implicit"])
                localComponent = entity["dataset-implicit"];

            var hasDataset = null;
            if (localComponent && localComponent["hasDataset"])
                hasDataset = localComponent["hasDataset"];
            var bestDataType = null;
            if (localComponent && localComponent["bestDataType"])
                bestDataType = localComponent["bestDataType"];
            var bestTypeScore = null;
            if (localComponent && localComponent["bestTypeScore"])
                bestTypeScore = localComponent["bestTypeScore"];

            string = toHtml(entity, topPos, pageIndex, hasDataset, bestDataType, bestTypeScore);
        }
        $('#detailed_annot-' + pageIndex).html(string);
        $('#detailed_annot-' + pageIndex).show();
    }

    function viewEntity(event) {
        if (responseJson == null)
            return;

        if (responseJson.mentions == null) {
            return;
        }

        mentions = responseJson.mentions;

        var localID = $(this).attr('id');
        //console.log(localID)
        
        if (mentions == null) {
            return;
        }

        var ind1 = localID.indexOf('-');
        //var ind2 = localID.indexOf('-', ind1+1);

        var localEntityNumber = parseInt(localID.substring(ind1+1));
        //console.log(localEntityNumber)

        if (localEntityNumber < mentions.length) {
            var entity = mentions[localEntityNumber];

            var localComponent = null;
            if (entity["dataset-implicit"])
                localComponent = entity["dataset-implicit"];

            var hasDataset = null;
            if (localComponent && localComponent["hasDataset"])
                hasDataset = localComponent["hasDataset"];
            var bestDataType = null;
            if (localComponent && localComponent["bestDataType"])
                bestDataType = localComponent["bestDataType"];
            var bestTypeScore = null;
            if (localComponent && localComponent["bestTypeScore"])
                bestTypeScore = localComponent["bestTypeScore"];

            var string = toHtml(mentions[localEntityNumber], -1, 0, hasDataset, bestDataType, bestTypeScore);
            $('#detailed_annot-0').html(string);
            $('#detailed_annot-0').show();
        }
    }

    function toHtml(entity, topPos, pageIndex, hasDataset, bestDataType, bestTypeScore) {
        var wikipedia = null;
        if (entity.wikipediaExternalRef)
            wikipedia = entity.wikipediaExternalRef;
        var wikidataId = null;
        if (entity.wikidataId)
            wikidataId = entity.wikidataId;

        var type = entity.type;

        var colorLabel = null;
        if (type)
            colorLabel = type;

        var definitions = null; 
        if (wikipedia)
            getDefinitions(wikipedia);

        var lang = null;
        if (entity.lang)
            lang = entity.lang;

        var content = entity.normalizedForm;
        var normalized = null;
        //if (wikipedia)
        //    normalized = getPreferredTerm(wikipedia);

        string = "<div class='info-sense-box " + colorLabel + "'";
        if (topPos != -1)
            string += " style='vertical-align:top; position:relative; top:" + topPos + "'";

        string += "><h4 style='color:#FFF;padding-left:10px;'>" + content.toUpperCase() +
            "</h4>";
        string += "<div class='container-fluid' style='background-color:#F9F9F9;color:#70695C;border:padding:5px;margin-top:5px;'>" +
            "<table style='width:100%;background-color:#fff;border:0px'><tr style='background-color:#fff;border:0px;margin-top:5px;'><td style='background-color:#fff;border:0px;'>";

        if (type) {
            if (type === 'dataset-implicit')
                string += "<p>Type: <b>implicit dataset</b></p>";
            else
                string += "<p>Type: <b>" + type + "</b></p>";
        }

        if (content)
            string += "<p>Raw name: <b>" + content + "</b></p>";                

        if (normalized)
            string += "<p>Normalized name: <b>" + normalized + "</b></p>";

        if (entity.confidence)
            string += "<p>conf: <i>" + entity.confidence + "</i></p>";

        if (type === "dataset-implicit" && bestDataType != null) {
            string += "<p>Likely data type: <b>" + bestDataType + "</b></p>";
            if (bestTypeScore != null)
               string += "<p>conf: <b>" + bestTypeScore + "</b></p>";
        }

        if (entity['data-device']) {
            string += "<p>Data acquisition device: <b>" + entity['data-device']['normalizedForm'] + "</b></p>";
        }

        if (entity['url']) {
            string += "<p>URL: <b>" + entity['url']['normalizedForm'] + "</b></p>";
        }

        if (wikipedia) {
            string += "</td><td style='align:right;bgcolor:#fff'>";
            string += '<span id="img-' + wikipedia + '-' + pageIndex + '"><script type="text/javascript">lookupWikiMediaImage("' + wikipedia + '", "' + 
                lang + '", "' + pageIndex + '")</script></span>';
        }

        string += "</td></tr></table>";

        if (entity.mentionContextAttributes || entity.documentContextAttributes) {
            string += "<br/><div style='width:100%;background-color:#fff;border:0px'>";

            if (entity.mentionContextAttributes) {

                if (entity.mentionContextAttributes.used.value || entity.mentionContextAttributes.created.value || entity.mentionContextAttributes.shared.value)
                    string += "<p>Mention-level: ";

                if (entity.mentionContextAttributes.used.value) {
                    string += "<b>used</b> (<i>" + entity.mentionContextAttributes.used.score.toFixed(3) + "</i>)";
                }

                if (entity.mentionContextAttributes.created.value) {
                    string += " - <b>created</b> (<i>" + entity.mentionContextAttributes.created.score.toFixed(3) + "</i>)";
                }

                if (entity.mentionContextAttributes.shared.value) {
                    string += " - <b>shared</b> (<i>" + entity.mentionContextAttributes.shared.score.toFixed(3) + "</i>)";
                }

                if (entity.mentionContextAttributes.used.value) 
                    string += "</p>";
            }

            if (entity.documentContextAttributes) {
                if (entity.documentContextAttributes.used.value || entity.documentContextAttributes.created.value || entity.documentContextAttributes.shared.value)
                    string += "<p>Document-level: ";

                if (entity.documentContextAttributes.used.value) {
                    string += "<b>used</b> (<i>" + entity.documentContextAttributes.used.score.toFixed(3) + "</i>)";
                }

                if (entity.documentContextAttributes.created.value) {
                    string += " - <b>created</b> (<i>" + entity.documentContextAttributes.created.score.toFixed(3) + "</i>)";
                }

                if (entity.documentContextAttributes.shared.value) {
                    string += " - <b>shared</b> (<i>" + entity.documentContextAttributes.shared.score.toFixed(3) + "</i>)";
                }

                if (entity.documentContextAttributes.used.value) {
                    string += "</p>";
                }
            }
            
            string += "</div>";
        }

        // bibliographical reference(s)
        if (entity['references']) {
            string += "<br/><p>References: "
            for(var r in entity['references']) {
                if (entity['references'][r]['label']) {
                    //string += "<b>" + entity['references'][r]['label'] + "</b>"    
                    localLabel = ""
                    localHtml = ""

                    if (entity['references'][r]['refKey'] != null && referenceMap[entity['references'][r]['refKey']]) {
                        //localHtml += entity['references'][r]['tei']
                        var doc = parse(referenceMap[entity['references'][r]['refKey']]);
                        var authors = doc.getElementsByTagName("author");
                        max = authors.length
                        if (max > 3)
                            max = 1;
                        for(var n=0; n < authors.length; n++) {
                            var lastName = "";
                            var firstName = "";
                            var localNodes = authors[n].getElementsByTagName("surname");
                            if (localNodes && localNodes.length > 0)
                                lastName = localNodes[0].childNodes[0].nodeValue;
                            localNodes = authors[n].getElementsByTagName("forename");
                            if (localNodes && localNodes.length > 0)
                                foreName = localNodes[0].childNodes[0].nodeValue;
                            if (n == 0 && authors.length > 3) {
                                localLabel += lastName + " et al";
                            } else if (n == 0) {
                                localLabel += lastName;
                            } else if (n == max-1 && authors.length <= 3) {
                                localLabel += " & " + lastName;
                            } else if (authors.length <= 3) { 
                                localLabel += ", " + lastName;
                            }
                        }

                        var dateNodes = doc.evaluate("//date[@type='published']/@when", doc, null, XPathResult.ANY_TYPE, null);
                        var date = dateNodes.iterateNext();
                        var dateStr = "";
                        if (date) {
                            var ind = date.textContent.indexOf("-");
                            var year  = date.textContent;
                            if (ind != -1)
                                year  = year.substring(0, ind);
                            localLabel += " (" + year + ")";
                        }

                        localHtml += displayBiblio(doc);
                    }

                    // make the biblio reference information collapsible
                    string += "<p><div class='panel-group' id='accordionParentReferences-" + pageIndex + "-" + r + "'>";
                    string += "<div class='panel panel-default'>";
                    string += "<div class='panel-heading' style='background-color:#FFF;color:#70695C;border:padding:0px;font-size:small;'>";
                    // accordion-toggle collapsed: put the chevron icon down when starting the page; accordion-toggle : put the chevron icon up
                    string += "<a class='accordion-toggle collapsed' data-toggle='collapse' data-parent='#accordionParentReferences-" + pageIndex + "-" + r + 
                        "' href='#collapseElementReferences-"+ pageIndex + "-" + r + "' style='outline:0;'>";
                    string += "<h5 class='panel-title' style='font-weight:normal;'>" + entity['references'][r]['label'] + " " + localLabel + "</h5>";
                    string += "</a>";
                    string += "</div>";
                    // panel-collapse collapse: hide the content of statemes when starting the page; panel-collapse collapse in: show it
                    string += "<div id='collapseElementReferences-"+ pageIndex + "-" + r +"' class='panel-collapse collapse'>";
                    string += "<div class='panel-body'>";
                    string += "<table class='statements' style='width:100%;background-color:#fff;border:1px'>" + localHtml + "</table>";
                    string += "</div></div></div></div></p>";
                }
            }
            string += "</p>"
        }

        if ((definitions != null) && (definitions.length > 0)) {
            var localHtml = wiki2html(definitions[0]['definition'], lang);
            string += "<p><div class='wiky_preview_area2'>" + localHtml + "</div></p>";
        }

        // statements
        var statements = null;
        if (wikipedia)
            statements = getStatements(wikipedia);
        if ((statements != null) && (statements.length > 0)) {
            var localHtml = "";
            for (var i in statements) {
                var statement = statements[i];
                localHtml += displayStatement(statement);
            }
//                string += "<p><div><table class='statements' style='width:100%;background-color:#fff;border:1px'>" + localHtml + "</table></div></p>";

            // make the statements information collapsible
            string += "<p><div class='panel-group' id='accordionParentStatements'>";
            string += "<div class='panel panel-default'>";
            string += "<div class='panel-heading' style='background-color:#FFF;color:#70695C;border:padding:0px;font-size:small;'>";
            // accordion-toggle collapsed: put the chevron icon down when starting the page; accordion-toggle : put the chevron icon up
            string += "<a class='accordion-toggle collapsed' data-toggle='collapse' data-parent='#accordionParentStatements' href='#collapseElementStatements"+ pageIndex + "' style='outline:0;'>";
            string += "<h5 class='panel-title' style='font-weight:normal;'>Wikidata statements</h5>";
            string += "</a>";
            string += "</div>";
            // panel-collapse collapse: hide the content of statemes when starting the page; panel-collapse collapse in: show it
            string += "<div id='collapseElementStatements"+ pageIndex +"' class='panel-collapse collapse'>";
            string += "<div class='panel-body'>";
            string += "<table class='statements' style='width:100%;background-color:#fff;border:1px'>" + localHtml + "</table>";
            string += "</div></div></div></div></p>";
        }

        if ((wikipedia != null) || (wikidataId != null)) {
            string += '<p>References: '
            if (wikipedia != null) {
                string += '<a href="http://' + lang + '.wikipedia.org/wiki?curid=' +
                    wikipedia +
                    '" target="_blank"><img style="max-width:28px;max-height:22px;margin-top:5px;" ' +
                    ' src="resources/img/wikipedia.png"/></a>';
            }
            if (wikidataId != null) {
                string += '<a href="https://www.wikidata.org/wiki/' +
                    wikidataId +
                    '" target="_blank"><img style="max-width:28px;max-height:22px;margin-top:5px;" ' +
                    ' src="resources/img/Wikidata-logo.svg"/></a>';
            }
            string += '</p>';
        }

        string += "</div></div>";

        return string;
    }

    function processChange() {
        var selected = $('#selectedService option:selected').attr('value');

        if (selected == 'annotateDatasetSentence') {
            createInputTextArea();
            setBaseUrl('annotateDatasetSentence');
        } else if (selected == 'annotateDatasetPDF') {
            createInputFile(selected);
            setBaseUrl('annotateDatasetPDF');
        } else if (selected == 'processDatasetTEI') {
            createInputFile(selected);
            setBaseUrl('processDatasetTEI');
        } else if (selected == 'processDatasetJATS') {
            createInputFile(selected);
            setBaseUrl('processDatasetJATS');
        }
    };

    function processChange2() {
        var selected = $('#selectedService2 option:selected').attr('value');

        if (selected == 'processDataseerSentence') {
            createInputTextArea2();
            setBaseUrl2('processDataseerSentence');
        } else if (selected == 'processDataseerPDF') {
            createInputFile2(selected);
            setBaseUrl2('processDataseerPDF');
        } else if (selected == 'processDataseerTEI') {
            createInputFile2(selected);
            setBaseUrl2('processDataseerTEI');
        } else if (selected == 'processDataseerJATS') {
            createInputFile2(selected);
            setBaseUrl2('processDataseerJATS');
        }
    };

    const wikimediaURL_prefix = 'https://';
    const wikimediaURL_suffix = '.wikipedia.org/w/api.php?action=query&prop=pageimages&format=json&pithumbsize=200&pageids=';

    wikimediaUrls = {};
    for (var i = 0; i < supportedLanguages.length; i++) {
        var lang = supportedLanguages[i];
        wikimediaUrls[lang] = wikimediaURL_prefix + lang + wikimediaURL_suffix
    }

    var imgCache = {};

    window.lookupWikiMediaImage = function (wikipedia, lang, pageIndex) {
        // first look in the local cache
        if (lang + wikipedia in imgCache) {
            var imgUrl = imgCache[lang + wikipedia];
            var document = (window.content) ? window.content.document : window.document;
            var spanNode = document.getElementById("img-" + wikipedia + "-" + pageIndex);
            spanNode.innerHTML = '<img src="' + imgUrl + '"/>';
        } else {
            // otherwise call the wikipedia API
            var theUrl = wikimediaUrls[lang] + wikipedia;

            // note: we could maybe use the en cross-lingual correspondence for getting more images in case of
            // non-English pages
            $.ajax({
                url: theUrl,
                jsonp: "callback",
                dataType: "jsonp",
                xhrFields: {withCredentials: true},
                success: function (response) {
                    var document = (window.content) ? window.content.document : window.document;
                    var spanNode = document.getElementById("img-" + wikipedia + "-" + pageIndex);
                    if (response.query && spanNode) {
                        if (response.query.pages[wikipedia]) {
                            if (response.query.pages[wikipedia].thumbnail) {
                                var imgUrl = response.query.pages[wikipedia].thumbnail.source;
                                spanNode.innerHTML = '<img src="' + imgUrl + '"/>';
                                // add to local cache for next time
                                imgCache[lang + wikipedia] = imgUrl;
                            }
                        }
                    }
                }
            });
        }
    };

    function getDefinitions(identifier) {
        var localEntity = conceptMap[identifier];
        if (localEntity != null) {
            return localEntity.definitions;
        } else
            return null;
    }

    function getCategories(identifier) {
        var localEntity = conceptMap[identifier];
        if (localEntity != null) {
            return localEntity.categories;
        } else
            return null;
    }

    function getMultilingual(identifier) {
        var localEntity = conceptMap[identifier];
        if (localEntity != null) {
            return localEntity.multilingual;
        } else
            return null;
    }

    function getPreferredTerm(identifier) {
        var localEntity = conceptMap[identifier];
        if (localEntity != null) {
            return localEntity.preferredTerm;
        } else
            return null;
    }

    function getStatements(identifier) {
        var localEntity = conceptMap[identifier];
        if (localEntity != null) {
            return localEntity.statements;
        } else
            return null;
    }

    function displayStatement(statement) {
        var localHtml = "";
        if (statement.propertyId) {
            if (statement.propertyName) {
                localHtml += "<tr><td>" + statement.propertyName + "</td>";
            } else if (statement.propertyId) {
                localHtml += "<tr><td>" + statement.propertyId + "</td>";
            }

            // value dislay depends on the valueType of the property
            var valueType = statement.valueType;
            if (valueType && (valueType == 'time')) {
                // we have here an ISO time expression
                if (statement.value) {
                    var time = statement.value.time;
                    if (time) {
                        var ind = time.indexOf("T");
                        if (ind == -1)
                            localHtml += "<td>" + time.substring(1) + "</td></tr>";
                        else
                            localHtml += "<td>" + time.substring(1, ind) + "</td></tr>";
                    }
                }
            } else if (valueType && (valueType == 'globe-coordinate')) {
                // we have some (Earth) GPS coordinates
                if (statement.value) {
                    var latitude = statement.value.latitude;
                    var longitude = statement.value.longitude;
                    var precision = statement.value.precision;
                    var gpsString = "";
                    if (latitude) {
                        gpsString += "latitude: " + latitude;
                    }
                    if (longitude) {
                        gpsString += ", longitude: " + longitude;
                    }
                    if (precision) {
                        gpsString += ", precision: " + precision;
                    }
                    localHtml += "<td>" + gpsString + "</td></tr>";
                }
            } else if (valueType && (valueType == 'string')) {
                if (statement.propertyId == "P2572") {
                    // twitter hashtag
                    if (statement.value) {
                        localHtml += "<td><a href='https://twitter.com/hashtag/" + statement.value.trim() + "?src=hash' target='_blank'>#" +
                            statement.value + "</a></td></tr>";
                    } else {
                        localHtml += "<td>" + "</td></tr>";
                    }
                } else {
                    if (statement.value) {
                        localHtml += "<td>" + statement.value + "</td></tr>";
                    } else {
                        localHtml += "<td>" + "</td></tr>";
                    }
                }
            } else if (valueType && (valueType == 'url')) {
                if (statement.value) {
                    if ( statement.value.startsWith("https://") || statement.value.startsWith("http://") ) {
                        localHtml += '<td><a href=\"'+ statement.value + '\" target=\"_blank\">' + statement.value + "</a></td></tr>";
                    } else {
                        localHtml += "<td>" + "</td></tr>";
                    }
                }
            } else {
                // default
                if (statement.valueName) {
                    localHtml += "<td>" + statement.valueName + "</td></tr>";
                } else if (statement.value) {
                    localHtml += "<td>" + statement.value + "</td></tr>";
                } else {
                    localHtml += "<td>" + "</td></tr>";
                }
            }
        }
        return localHtml;
    }

    function displayBiblio(doc) {
        // authors
        var authors = doc.getElementsByTagName("author");
        var localHtml = "<tr><td>authors</td><td>";
        for(var n=0; n < authors.length; n++) {

            var lastName = "";
            var localNodes = authors[n].getElementsByTagName("surname");
            if (localNodes && localNodes.length > 0)
                lastName = localNodes[0].childNodes[0].nodeValue;
            
            var foreNames = [];
            localNodes = authors[n].getElementsByTagName("forename");
            if (localNodes && localNodes.length > 0) {
                for(var m=0; m < localNodes.length; m++) {
                    foreNames.push(localNodes[m].childNodes[0].nodeValue);
                }
            }

            if (n != 0)
                localHtml += ", ";
            for(var m=0; m < foreNames.length; m++) {
                localHtml += foreNames[m];
            }
            localHtml += " " + lastName;
        }
        localHtml += "</td></tr>";

        // article title
        var titleNodes = doc.evaluate("//title[@level='a']", doc, null, XPathResult.ANY_TYPE, null);
        var theTitle = titleNodes.iterateNext();
        if (theTitle) {
            localHtml += "<tr><td>title</td><td>"+theTitle.textContent+"</td></tr>";
        }

        // date
        var dateNodes = doc.evaluate("//date[@type='published']/@when", doc, null, XPathResult.ANY_TYPE, null);
        var date = dateNodes.iterateNext();
        if (date) {
            localHtml += "<tr><td>date</td><td>"+date.textContent+"</td></tr>";
        }
        

        // journal title
        titleNodes = doc.evaluate("//title[@level='j']", doc, null, XPathResult.ANY_TYPE, null);
        var theTitle = titleNodes.iterateNext();
        if (theTitle) {
            localHtml += "<tr><td>journal</td><td>"+theTitle.textContent+"</td></tr>";
        }

        // monograph title
        titleNodes = doc.evaluate("//title[@level='m']", doc, null, XPathResult.ANY_TYPE, null);
        theTitle = titleNodes.iterateNext();
        if (theTitle) {
            localHtml += "<tr><td>book title</td><td>"+theTitle.textContent+"</td></tr>";
        }

        // conference
        meetingNodes = doc.evaluate("//meeting", doc, null, XPathResult.ANY_TYPE, null);
        theMeeting = meetingNodes.iterateNext();
        if (theMeeting) {
            localHtml += "<tr><td>conference</td><td>"+theMeeting.textContent+"</td></tr>";
        }

        // address
        addressNodes = doc.evaluate("//address", doc, null, XPathResult.ANY_TYPE, null);
        theAddress = addressNodes.iterateNext();
        if (theAddress) {
            localHtml += "<tr><td>address</td><td>"+theAddress.textContent+"</td></tr>";
        }

        // volume
        var volumeNodes = doc.evaluate("//biblScope[@unit='volume']", doc, null, XPathResult.ANY_TYPE, null);
        var volume = volumeNodes.iterateNext();
        if (volume) {
            localHtml += "<tr><td>volume</td><td>"+volume.textContent+"</td></tr>";
        }

        // issue
        var issueNodes = doc.evaluate("//biblScope[@unit='issue']", doc, null, XPathResult.ANY_TYPE, null);
        var issue = issueNodes.iterateNext();
        if (issue) {
            localHtml += "<tr><td>issue</td><td>"+issue.textContent+"</td></tr>";
        }

        // pages
        var pageNodes = doc.evaluate("//biblScope[@unit='page']/@from", doc, null, XPathResult.ANY_TYPE, null);
        var firstPage = pageNodes.iterateNext();
        if (firstPage) {
            localHtml += "<tr><td>first page</td><td>"+firstPage.textContent+"</td></tr>";
        }
        pageNodes = doc.evaluate("//biblScope[@unit='page']/@to", doc, null, XPathResult.ANY_TYPE, null);
        var lastPage = pageNodes.iterateNext();
        if (lastPage) {
            localHtml += "<tr><td>last page</td><td>"+lastPage.textContent+"</td></tr>";
        }
        pageNodes = doc.evaluate("//biblScope[@unit='page']", doc, null, XPathResult.ANY_TYPE, null);
        var pages = pageNodes.iterateNext();
        if (pages && pages.textContent != null && pages.textContent.length > 0) {
            localHtml += "<tr><td>pages</td><td>"+pages.textContent+"</td></tr>";
        }

        // issn
        var issnNodes = doc.evaluate("//idno[@type='ISSN']", doc, null, XPathResult.ANY_TYPE, null);
        var issn = issnNodes.iterateNext();
        if (issn) {
            localHtml += "<tr><td>ISSN</td><td>"+issn.textContent+"</td></tr>";
        }
        issnNodes = doc.evaluate("//idno[@type='ISSNe']", doc, null, XPathResult.ANY_TYPE, null);
        var issne = issnNodes.iterateNext();
        if (issne) {
            localHtml += "<tr><td>e ISSN</td><td>"+issne.textContent+"</td></tr>";
        }

        //doi
        var doiNodes = doc.evaluate("//idno[@type='DOI']", doc, null, XPathResult.ANY_TYPE, null);
        var doi = doiNodes.iterateNext();
        if (doi && doi.textContent) {
            //if (doi.textContent.startsWith("10."))
                localHtml += "<tr><td>DOI</td><td><a href=\"https://doi.org/" + doi.textContent + 
                    "\" target=\"_blank\" style=\"color:#BC0E0E;\">"+doi.textContent+"</a></td></tr>";
            /*else 
                localHtml += "<tr><td>DOI</td><td><a href=\"https://doi.org/" + doi.textContent + "\">"+doi.textContent+"</a></td></tr>";*/
        }

        // PMC
        var pmcNodes = doc.evaluate("//idno[@type='PMCID']", doc, null, XPathResult.ANY_TYPE, null);
        var pmc = pmcNodes.iterateNext();
        if (pmc && pmc.textContent) {
            localHtml += "<tr><td>PMC ID</td><td><a href=\"https://www.ncbi.nlm.nih.gov/pmc/articles/" + pmc.textContent + 
                "/\" target=\"_blank\" style=\"color:#BC0E0E;\">"+pmc.textContent+"</a></td></tr>";
        }

        // PMID
        var pmidNodes = doc.evaluate("//idno[@type='PMID']", doc, null, XPathResult.ANY_TYPE, null);
        var pmid = pmidNodes.iterateNext();
        if (pmid && pmid.textContent) {
            localHtml += "<tr><td>PMID</td><td><a href=\"https://pubmed.ncbi.nlm.nih.gov/" + pmid.textContent + 
                "/\" target=\"_blank\" style=\"color:#BC0E0E;\">"+pmid.textContent+"</a></td></tr>";
        }

        // Open Access full text
        var oaNodes = doc.evaluate("//ptr[@type='open-access']/@target", doc, null, XPathResult.ANY_TYPE, null);
        var oa = oaNodes.iterateNext();
        if (oa && oa.textContent) {
            localHtml += "<tr><td>Open Access</td><td><a href=\"" + oa.textContent + "/\" target=\"_blank\" style=\"color:#BC0E0E;\">"+
                oa.textContent+"</a></td></tr>";
        }

        // publisher
        var publisherNodes = doc.evaluate("//publisher", doc, null, XPathResult.ANY_TYPE, null);
        var publisher = publisherNodes.iterateNext();
        if (publisher) {
            localHtml += "<tr><td>publisher</td><td>"+publisher.textContent+"</td></tr>";
        }

        // editor
        var editorNodes = doc.evaluate("//editor", doc, null, XPathResult.ANY_TYPE, null);
        var editor = editorNodes.iterateNext();
        if (editor) {
            localHtml += "<tr><td>editor</td><td>"+editor.textContent+"</td></tr>";
        }

        return localHtml;
    }

    function isEmpty(element){
        return !$.trim(element.html())
    }

    function createInputFile() {
        $('#textInputDiv').hide();
        $('#fileInputDiv').show();

        $('#gbdForm').attr('enctype', 'multipart/form-data');
        $('#gbdForm').attr('method', 'post');
    }

    function createInputFile2() {
        $('#textInputDiv2').hide();
        $('#fileInputDiv2').show();

        $('#gbdForm2').attr('enctype', 'multipart/form-data');
        $('#gbdForm2').attr('method', 'post');
    }

    function createInputTextArea() {
        $('#fileInputDiv').hide();
        $('#textInputDiv').show();
    }

    function createInputTextArea2() {
        $('#fileInputDiv2').hide();
        $('#textInputDiv2').show();
    }

    function parse(xmlStr) {
        var parseXml;

        if (typeof window.DOMParser != "undefined") {
            return ( new window.DOMParser() ).parseFromString(xmlStr, "text/xml");
        } else if (typeof window.ActiveXObject != "undefined" &&
               new window.ActiveXObject("Microsoft.XMLDOM")) {
            var xmlDoc = new window.ActiveXObject("Microsoft.XMLDOM");
            xmlDoc.async = "false";
            xmlDoc.loadXML(xmlStr);
            return xmlDoc;
        } else {
            throw new Error("No XML parser found");
        }
    }

    var examples = ["Insulin levels of all samples were measured by ELISA kit (Mercodia).", 
    "Temperatures and depths of oil reservoirs were retrieved from the well logs.",
    "The cap was then tightened to create a hypoxic condition (with DO of 0.8 mg/L, measured using a HI 98194 Multiparameter Waterproof Meter, Hanna instruments, Romania).",    
    "The sequences were analysed using the ION PGM system with ion 316 chip kit V2 (Life Technologies, CA, USA) following the manufacturer's recommended protocols."    
    ]

    function setExamples(rank) {
        for (var ind in examples) {
            $('#example'+rank+'_'+ind).bind('click', function (event) {
                var localID = $(this).attr('id');
                var local_ind_pos = localID.indexOf("_");
                var local_ind = localID.substring(local_ind_pos+1);
                var local_rank = localID.substring(local_ind_pos-1, local_ind_pos);
                $('#inputTextArea'+rank).val(examples[local_ind]);
            });
            /*$('#example'+ind).bind('click', function (event) {
                event.preventDefault();
                $('#inputTextArea'+rank).val(examples[ind]);
            });
            $('#example'+ind).bind('click', function (event) {
                event.preventDefault();
                $('#inputTextArea'+rank).val(examples[ind]);
            });
            $('#example'+ind).bind('click', function (event) {
                event.preventDefault();
                $('#inputTextArea'+rank).val(examples[ind]);
            });*/
        }
    }

    

})(jQuery);



