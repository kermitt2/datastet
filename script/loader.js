/**
 * Simple javascript for loading the TEI documents under resources/dataset/corpus
 * on the web application databse.
 */

'use strict';

var fs = require('fs'),
    path = require('path'),
    request = require("request");

const teiPath = "../resources/dataset/dataseer/corpus/";
const serviceEndpoint = "/backoffice/upload/"

function load(option) {
    var url = "http://" + option.host
    if (option.port) 
        url += ":" + option.port
    url += serviceEndpoint
    fs.readdir(teiPath, function(err, list) {
        list.filter(extension).forEach(function(value) {
            console.log(value);
            var formData = {
                //dataseerML: true,
                uploadedFiles: fs.createReadStream(teiPath + "/" + value),
                uploadedFiles: {
                    value:  fs.createReadStream(teiPath + "/" + value),
                    options: {
                        filename: value,
                        contentType: 'text/xml'
                    }
                }
            };
            console.log(url);
            request.post({url: url, formData: formData}, function optionalCallback(err, httpResponse, body) {
                if (err) {
                    return console.error('upload failed:', err);
                }
                console.log('Upload successful!  Server responded with:', body);
            });
        });
    });
}


/**
 * Init the main object with paths passed with the command line
 */
function init() {
    var options = new Object();

    // first get the config
    const config = require('./config.json');
    options.host = config.host;
    options.port = config.port;

    return options;
}


function extension(element) {
    var extName = path.extname(element);
    return extName === '.xml'; 
}

function end() {
    var this_is_the_end = new Date() - start;
    console.info('Execution time: %dms', this_is_the_end)
}

var start;

function main() {
    var options = init();
    start = new Date();
    load(options);
}

main();