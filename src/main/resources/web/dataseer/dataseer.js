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

    function defineBaseURL(ext) {
        var baseUrl = null;
        ext = "service/" + ext;
        if ($(location).attr('href').indexOf("index.html") != -1)
            baseUrl = $(location).attr('href').replace("index.html", ext);
        else
            baseUrl = $(location).attr('href') + ext;
        return baseUrl;
    }

    function setBaseUrl(ext) {
        var baseUrl = defineBaseURL(ext);
        $('#gbdForm').attr('action', baseUrl);
    }

    $(document).ready(function () {

        $("#subTitle").html("About");
        $("#divAbout").show();
        $("#divRestI").hide();
        $("#divDoc").hide();

        createInputTextArea('text');
        setBaseUrl('processDataseerSentence');
        $('#example0').bind('click', function (event) {
            event.preventDefault();
            $('#inputTextArea').val(examples[0]);
        });
        $('#example1').bind('click', function (event) {
            event.preventDefault();
            $('#inputTextArea').val(examples[1]);
        });
        $('#example2').bind('click', function (event) {
            event.preventDefault();
            $('#inputTextArea').val(examples[2]);
        });
        $('#example3').bind('click', function (event) {
            event.preventDefault();
            $('#inputTextArea').val(examples[3]);
        });

        $("#selectedService").val('processDataseerSentence');
        $('#selectedService').change(function () {
            processChange();
            return true;
        });

        $('#submitRequest').bind('click', submitQuery);

        $("#about").click(function () {
            $("#about").attr('class', 'section-active');
            $("#rest").attr('class', 'section-not-active');
            $("#doc").attr('class', 'section-not-active');
            $("#demo").attr('class', 'section-not-active');

            $("#subTitle").html("About");
            $("#subTitle").show();

            $("#divAbout").show();
            $("#divRestI").hide();
            $("#divDoc").hide();
            $("#divDemo").hide();
            return false;
        });
        $("#rest").click(function () {
            $("#rest").attr('class', 'section-active');
            $("#doc").attr('class', 'section-not-active');
            $("#about").attr('class', 'section-not-active');
            $("#demo").attr('class', 'section-not-active');

            $("#subTitle").hide();
            //$("#subTitle").show();
            processChange();

            $("#divRestI").show();
            $("#divAbout").hide();
            $("#divDoc").hide();
            $("#divDemo").hide();
            return false;
        });
        $("#doc").click(function () {
            $("#doc").attr('class', 'section-active');
            $("#rest").attr('class', 'section-not-active');
            $("#about").attr('class', 'section-not-active');
            $("#demo").attr('class', 'section-not-active');

            $("#subTitle").html("Doc");
            $("#subTitle").show();

            $("#divDoc").show();
            $("#divAbout").hide();
            $("#divRestI").hide();
            $("#divDemo").hide();
            return false;
        });
    });

    function ShowRequest(formData, jqForm, options) {
        var queryString = $.param(formData);
        $('#infoResult').html('<font color="red">Requesting server...</font>');
        return true;
    }

    function AjaxError(jqXHR, textStatus, errorThrown) {
        $('#infoResult').html("<font color='red'>Error encountered while requesting the server.<br/>" + jqXHR.responseText + "</font>");
        entities = null;
    }

    function AjaxError2(message) {
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

        var selected = $('#selectedService option:selected').attr('value');
        var urlLocal = $('#gbdForm').attr('action');
        if (selected == 'processDataseerSentence') {
            {
                $.ajax({
                    type: 'GET',
                    url: urlLocal,
                    data: {text: $('#inputTextArea').val()},
                    success: SubmitSuccesful,
                    error: AjaxError,
                    contentType: false
                    //dataType: "text"
                });
            }
        }
        else if (selected == 'processDataseerTEI' || selected == 'processDataseerJATS' || selected == 'processDataseerPDF') {
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
        /*else if (selected == 'annotateDataseerPDF') {
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
                    AjaxError2("Response " + xhr.status + ": ");
                }
            };
            xhr.send(formData);
        }*/
    }

    function SubmitSuccesful(responseText, statusText) {
        var selected = $('#selectedService option:selected').attr('value');

        if (selected == 'processDataseerSentence') {
            SubmitSuccesfulText(responseText, statusText);
        } else if (selected == 'processDataseerPDF') {
            SubmitSuccesfulXML(responseText, statusText);
        } else if (selected == 'processDataseerTEI') {
            SubmitSuccesfulXML(responseText, statusText);
        } else if (selected == 'processDataseerJATS') {
            SubmitSuccesfulXML(responseText, statusText);
        } 
        /*else if (selected == 'annotateDataseerPDF') {
            SubmitSuccesfulPDF(responseText, statusText);          
        }*/
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

        $('#requestResult').html(display);
        window.prettyPrint && prettyPrint();

        $('#requestResult').show();
    }

    function SubmitSuccesfulXML(responseText, statusText) {
        responseXML = responseText;
        if ((responseXML == null) || (responseXML.length == 0)) {
            $('#infoResult')
                .html("<font color='red'>Error encountered while receiving the server's answer: response is empty.</font>");
            return;
        } else {
            $('#infoResult').html('');
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

        $('#requestResult').html(display);
        window.prettyPrint && prettyPrint();

        $('#requestResult').show();
    }

    function processChange() {
        var selected = $('#selectedService option:selected').attr('value');

        if (selected == 'processDataseerSentence') {
            createInputTextArea();
            //$('#consolidateBlock').show();
            setBaseUrl('processDataseerSentence');
        } else if (selected == 'processDataseerPDF') {
            createInputFile(selected);
            setBaseUrl('processDataseerPDF');
        } else if (selected == 'processDataseerTEI') {
            createInputFile(selected);
            setBaseUrl('processDataseerTEI');
        } else if (selected == 'processDataseerJATS') {
            createInputFile(selected);
            setBaseUrl('processDataseerJATS');
        }

        /*else if (selected == 'annotateSoftwarePDF') {
            createInputFile(selected);
            //$('#consolidateBlock').hide();
            setBaseUrl('annotateSoftwarePDF');
        }*/
    };

    /*const wikimediaURL_prefix = 'https://';
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
        var doiNodes = doc.evaluate("//idno[@type='doi']", doc, null, XPathResult.ANY_TYPE, null);
        var doi = doiNodes.iterateNext();
        if (doi && doi.textContent) {
            //if (doi.textContent.startsWith("10."))
                localHtml += "<tr><td>DOI</td><td><a href=\"https://doi.org/" + doi.textContent + "\" target=\"_blank\">"+doi.textContent+"</a></td></tr>";
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
    }*/

    function createInputFile(selected) {
        $('#textInputDiv').hide();
        $('#fileInputDiv').show();

        $('#gbdForm').attr('enctype', 'multipart/form-data');
        $('#gbdForm').attr('method', 'post');
    }

    function createInputTextArea() {
        $('#fileInputDiv').hide();
        $('#textInputDiv').show();
    }

    /*function parse(xmlStr) {
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
    }*/

    var examples = ["Insulin levels of all samples were measured by ELISA kit (Mercodia).", 
    "Temperatures and depths of oil reservoirs were retrieved from the well logs.",
    "The cap was then tightened to create a hypoxic condition (with DO of 0.8 mg/L, measured using a HI 98194 Multiparameter Waterproof Meter, Hanna instruments, Romania).",    
    "The sequences were analysed using the ION PGM system with ion 316 chip kit V2 (Life Technologies, CA, USA) following the manufacturer's recommended protocols."    
    ]

})(jQuery);



