# DataStet

[![License](http://img.shields.io/:license-apache-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)
[![Docker Hub](https://img.shields.io/docker/pulls/grobid/datastet.svg)](https://hub.docker.com/r/grobid/datastet "Docker Pulls")
[![SWH](https://archive.softwareheritage.org/badge/origin/https://github.com/kermitt2/datastet/)](https://archive.softwareheritage.org/browse/origin/?origin_url=https://github.com/kermitt2/datastet)

DataStet is originally a fork from [dataseer-ml](https://github.com/dataseer/dataseer-ml). This extended and improved version aims at identifying every mention of datasets in scientific documents, including implicit mentions of datasets (introduction of data created/used in the research work, but not named) and explicitly named dataset. In addition, this version includes an automatic characterization of the mention context. 

Most of the datasets discussed in scientific articles are actually not named, but these data are part of the disclosed scientific work and should be shared properly to meet the [FAIR](https://en.wikipedia.org/wiki/FAIR_data) requirements. Named dataset are particularly useful to evaluate the impact of a datasets in other research works and to credit researchers developing datasets as valuable scientific contributions (beyond just scholar publications).

Mentions of dataset are characterized automatically as _used_ or not in the research work described in the scientific document, _created_ and _shared_. 

The identified datasets are further classified in a hierarchy of dataset types, these data types being directly derived from MeSH.

![GROBID Dataset mentions Demo](doc/images/screen03.png)

![GROBID Dataset mentions Demo](doc/images/screen02.png)

The DataStet service can process a variety of scientific article formats, including mainstream publisher's native XML submission formats: PDF, TEI, JATS/NLM, ScholarOne, BMJ, Elsevier staging format, OUP, PNAS, RSC, Sage, Wiley, etc. PDF is considered as the "universal" scientific document format, but it is also the most challenging one. We use GROBID to process and structure efficiently and reliably PDF. 

The back-end service remain compatible with [dataseer-ml](https://github.com/dataseer/dataseer-ml) and the [DataSeer-Web application](https://github.com/dataseer/dataseer-web).  

A python client is available for processing at scale in parallel scientific articles using DataStet. 

For identifying mentions of software in scientific literature, an application with a very similar approach has been developed called [Sofcite software mention recognizer](https://github.com/softcite/software-mentions). The above-mentioned client can use both DataStet and Softcite to produce both dataset and software mention annotations. 

## Approach

See the following article for simple description of the approach, used training data, evaluations and how the tool is used in the [French Open Science Monitor]():

```
Aricia Bassinet, Laetitia Bracco, Anne L'Hôte, Eric Jeangirard, Patrice Lopez, and Laurent Romary. "Large-scale Machine-Learning analysis of scientific PDF for monitoring the production and the openness of research data and software in France". 2023. [hal-04121339v3]. https://hal.science/hal-04121339v3
```

### Dataset identification and classification

The processing of an article follows 7 steps: 

1. __Structuring__: Given an article to be processed by DataStet:

____1.1. if the format is __PDF__, the document is first parsed and structured automatically by [Grobid](https://github.com/kermitt2/grobid). This includes metadata extraction, structuring the text body and bibliographical references. 

____1.2. if the format is a __publisher XML__ format (see [Pub2TEI](https://github.com/kermitt2/Pub2TEI) for the list of supported XML formats, e.g. TEI, JATS/NLM, ScholarOne, BMJ, Elsevier staging format, OUP, PNAS, RSC, Sage, Wiley, etc.), [Pub2TEI](https://github.com/kermitt2/Pub2TEI) converts the XML to the same customised structured TEI representation as GROBID. 

2. __Selection of data relevant zones__: Based on the structured document, some sections are flagged to be processed for dataset mention identification and some will be skipped (title, keyword, bibliographical references, section header, figure and table content). The structures to be processed are further flagged as relevant for impicit dataset mentions (normal paragraphs, Data availability Sections) or not (annexes, footnotes). The objective is to ignore some structures when identifying named dataset mentions, and ignore some additional structures when identifying implicit dataset mentions, to avoid obvious spurious extractions. 

3. __Sentence segmentation__: The zones marked as relevant for dataset identication (for named datasets and/or implicit datasets) are then segmented into sentences thanks to one of the two available tools (the Pragmatic Segmenter or OpenNLP as selected in the configuration), with some customization to better support scientific texts (i.e. avoiding wrong sentence break in the middle of reference callout or in the middle of scientific notations, and taking into account section and paragraph breaks as identified in the structure recognition step in GROBID). 

4. __Sentence processing__: 

____4.1. __Sentence labeling__: Each sentence from the dataset relevant zones is then processed by a sequence labeling model to label dataset names (explicit dataset), dataset implicitely mentioned (unamed datasets), and data acquisition devices (name of a device used to produce data). Labeling model is a LinkBERT-base fine-tune model integrated via [DeLFT](https://github.com/kermitt2/delft). 

____4.2 __Attribute attachment__: Recognized URL (based on pattern matching and PDF URL annotations) in the sentence are possibly attached to dataset mention. 

____4.3. __Mention disambiguation and filtering__: An entity disambiguation is realized for all recognized dataset mention using the full sentence as disambiguation context with [entity-fishing](https://github.com/kermitt2/entity-fishing).  If the mention is explicitely recognized as an entity not related to datasets with high confidence, the mention will be filtered out. 

____4.4. __Sentence classification__: Each sentence is going through a cascade of text classifiers, all based on a fine-tuned [SciBERT](https://github.com/allenai/scibert) deep learning architecture integrated in Java via [DeLFT](https://github.com/kermitt2/delft) and [JEP](https://github.com/ninia/jep), to predict if the sentence introduce a dataset ("dataset sentence"), and if yes, which dataset type and sub type is introduced based on a hierarchy of dataset types derived from the MeSH taxonomy. 

____4.5 __Mention propagations__: A document-level propagation of mentions of named mentions (not implicit datasets) is realized controled by a tf-idf measure. For each named dataset identified in step 4.1, if the same named dataset string appears elsewhere the document and above a given tf/idf threshold (to keep only unfrequent strings), it is also labeled as software mention.

5. __Mention filtering__: Recognized implicit datasets not having URL attachement corresponding to known dataset locations and DOI pattern are filtered out if they appear in a sentence with a dataset sentence classification score below 0.5, as produced in step 4.3. 

6. __Bibliographical reference attachments__: Bibliographical reference markers in proximity to dataset mentions (named or implicit) are attached to the mention based on attachment rules. 

7. __Characterization of mention contexts__: Each dataset mention is classified as whether the dataset is used, created and shared in the research work described in the scientific article:

    * __used__: the mentioned dataset is used by the research work disclosed in the document
    * __created__: the mentioned dataset is a creation of the research work disclosed in the document or the object of a contribution of the research work
    * __shared__: the dataset is claimed to be shared publicly via a sharing statement (note: this does not necessarily means that the dataset is available in Open Access, it might required conditions for access - statements like "data accessible on reasonable request" are not considered as valid sharing statements)

For predicting the characterization of the mention context, three binary classifiers are used, implemented with LinkBERT-base fine-tuned models integrated via [DeLFT](https://github.com/kermitt2/delft).

## Run the service with docker

The easiest way to deploy and run the service is to use the Docker image. 

It's possible to use a Docker image via [docker HUB](https://hub.docker.com/r/grobid/datastet), pull the image (5.25GB) as follow: 

```bash
docker pull grobid/datastet:0.8.1
```

(check the latest version on project's [docker HUB](https://hub.docker.com/r/grobid/datastet)!)

After pulling or building the Docker image, you can now run the `datastet` service as a container:

```bash
>  docker run --rm --gpus all -it --init --ulimit core=0 -p 8060:8060 grobid/datastet:0.8.1
```

The build image includes the automatic support of GPU when available on the host machine via the parameter `--gpus all` (with automatic recognition of the CUDA version), with fall back to CPU if GPU are not available. The support of GPU is only available on Linux host machine.

The `datastet` service is available at the default host/port `localhost:8060`, but it is possible to map the port at launch time of the container as follow:

```bash
> docker run --rm --gpus all -it --init --ulimit core=0 -p 8080:8060 grobid/datastet:0.8.1
```

By default, a fine-tuned LinkBERT base model if used for the dataset mention recognition (it performs better than SciBERT). Dataset type classification models are realized with fine-tuned SciBERT models. Dataset mention context characterization is based on fine-tuned LinkBERT base models. To modify the configuration without rebuilding the image - for instance rather use the SciBERT model, it is possible to mount a modified config file at launch as follow: 

```bash
> docker run --rm --gpus all -it --init --ulimit core=0 -p 8060:8060 -v /home/lopez/grobid/datastet/resources/config/config.yml:/opt/grobid/datastet/resources/config/config.yml:ro  grobid/datastet:0.8.1
```

As an alterntive, a docker image for the `datastet` service can be built with the project Dockerfile to match the current master version. The complete process is as follow: 

- copy the `Dockerfile.datastet` at the root of the GROBID installation:

```bash
~/grobid/datastet$ cp ./Dockerfile.datastet ..
```

- from the GROBID root installation (`grobid/`), launch the docker build:

```bash
> docker build -t grobid/datastet:0.8.1 --build-arg GROBID_VERSION=0.8.1 --file Dockerfile.datastet .
```

The Docker image build take several minutes, installing GROBID, datastet, a complete Python Deep Learning environment based on [DeLFT](https://github.com/kermitt2/delft) and deep learning models downloaded from the internet (one fine-tuned model with a BERT layer has a size of around 400 MB). The resulting image is thus very large, more than 10GB, due to the deep learning resources and models. 


## Python client

To exploit the DataStet service efficiently (concurrent calls) and robustly, a Python client is available [here](https://github.com/softcite/software_mentions_client). This client is common to the Softcite Software Mention Recognizer and can be used with DataStet too.

If you want to process a directory of PDF and/or XML documents, this is the best and simplest solution: deploy a Docker image of the DataStet server and use this client with the option `--datastet`.

## Build & Run

Building the module requires JDK 1.11 or higher. First install and build the latest development version of GROBID (currently `0.8.1`) as explained by the [documentation](http://grobid.readthedocs.org), together with [DeLFT](https://github.com/kermitt2/delft) (currently version `0.3.3`) for Deep Learning model support.

Under the installed and built `grobid/` directory, clone the present module `datastet` (it will appear as sibling sub-project to grobid-core, grobid-trainer, etc.):

> cd grobid/

> git clone https://github.com/kermitt2/datastet

Download from AWS S3 and install the trained models in the standard grobid-home path:

> ./gradlew installModels 

Try compiling everything with:

> ./gradlew clean install 

Run some test: 

> ./gradlew test

To start the service:

> ./gradlew run

## Console web app

Javascript demo/console web app is then accessible at ```http://localhost:8060```. From the console and the `Dataset services` tab, you can process chunk of text (select `Process text sentence`) or process a complete PDF document (select `process PDF`). 

Legacy/deprecated dataseer-ml services are available with the `Dataseer services` tab. 

## JSON format for the extracted software mention

The resulting dataset mention extractions include various attributes and information. These extractions follow the [JSON format documented on this page](https://github.com/kermitt2/datastet/blob/master/doc/annotation_schema.md). 

## Web API

#### /service/annotateDatasetPDF

|  method   |  request type         |  response type       |  parameters         |  requirement  |  description  |
|---        |---                    |---                   |---                  |---            |---            |
| POST      | `multipart/form-data` | `application/json`   | `input`             | required      | PDF file to be processed |
|           |                       |                      | `disambiguate`      | optional      | `disambiguate` is a string of value `0` (no disambiguation, default value) or `1` (disambiguate and inject Wikidata entity id and Wikipedia pageId) |

Response status codes:

|     HTTP Status code |   reason                                               |
|---                   |---                                                     |
|         200          |     Successful operation.                              |
|         204          |     Process was completed, but no content could be extracted and structured |
|         400          |     Wrong request, missing parameters, missing header  |
|         500          |     Indicate an internal service error, further described by a provided message           |
|         503          |     The service is not available, which usually means that all the threads are currently used                       |

A `503` error normally means that all the threads available to Softcite service are currently used for processing concurrent requests. The client need to re-send the query after a wait time that will allow the server to free some threads. The wait time depends on the service and the capacities of the server, we suggest 5 seconds for the `annotateDatasetPDF` service or 3 seconds when disambiguation is also requested.

Using ```curl``` POST request with a __PDF file__:

```console
curl --form input=@./src/test/resources/PMC1636350.pdf --form disambiguate=1 localhost:8060/service/annotateDatasetPDF
```

For PDF, each entity will be associated with a list of bounding box coordinates relative to the PDF, see [here](https://grobid.readthedocs.io/en/latest/Coordinates-in-PDF/#coordinate-system-in-the-pdf) for more explanation about the coordinate system. 

In addition, the response will contain the bibliographical reference information associated to a dataset mention when found. The bibliographical information are provided in XML TEI (similar format as GROBID). 


### /service/annotateDatasetSentence

Identify the dataset information in a sentence and optionally disambiguate the extracted dataset mentions against Wikidata. This is mainly for testing purposes, as the normal input is a full document. 

|  method   |  request type         |  response type     |  parameters            |  requirement  |  description  |
|---        |---                    |---                 |---                     |---            |---            |
| GET, POST | `multipart/form-data` | `application/json` | `text`            | required      | the text to be processed |
|           |                       |                    | `disambiguate` | optional      | `disambiguate` is a string of value `0` (no disambiguation, default value) or `1` (disambiguate and inject Wikidata entity id and Wikipedia pageId) |

Response status codes:

|     HTTP Status code |   reason                                               |
|---                   |---                                                     |
|         200          |     Successful operation.                              |
|         204          |     Process was completed, but no content could be extracted and structured |
|         400          |     Wrong request, missing parameters, missing header  |
|         500          |     Indicate an internal service error, further described by a provided message           |
|         503          |     The service is not available, which usually means that all the threads are currently used                       |

A `503` error normally means that all the threads available to Softcite service are currently used for processing concurrent requests. The client need to re-send the query after a wait time that will allow the server to free some threads. The wait time depends on the service and the capacities of the server, we suggest 1 seconds for the `annotateDatasetSentence` service.

Using ```curl``` POST/GET requests with some __text__:

```console
curl -X POST -d "text=Insulin levels of all samples were measured by ELISA kit (Mercodia)." localhost:8060/service/annotateDatasetSentence
```

```console
curl -GET --data-urlencode "text=Insulin levels of all samples were measured by ELISA kit (Mercodia)." localhost:8060/service/annotateDatasetSentence
```

which should return this:

```json
{
  "application" : "datastet",
  "version" : "0.7.2-SNAPSHOT",
  "date" : "2022-06-15T12:25+0000",
  "mentions" : [ {
    "rawForm" : "Insulin levels",
    "type" : "dataset-implicit",
    "dataset-implicit" : {
      "rawForm" : "Insulin levels",
      "normalizedForm" : "Insulin levels",
      "offsetStart" : 0,
      "offsetEnd" : 14,
      "bestDataType" : "tabular data",
      "bestTypeScore" : 0.9956,
      "hasDataset" : 0.9976811408996582
    },
    "data-device" : {
      "rawForm" : "ELISA kit (Mercodia)",
      "normalizedForm" : "ELISA kit (Mercodia)",
      "offsetStart" : 47,
      "offsetEnd" : 67
    },
    "normalizedForm" : "Insulin levels",
    "context" : "Insulin levels of all samples were measured by ELISA kit (Mercodia)."
  } ],
  "runtime" : 0.901
}
```

Runtimes are expressed in milliseconds. 

## Training data

See https://github.com/kermitt2/dataset_recognition_resources

Binary models for characterization of mention contexts are the same as for Softcite, trained with https://zenodo.org/records/8033868

Data Availability Sections are recognized via GROBID. 

## Contact and License

Main author and contact: Patrice Lopez (<patrice.lopez@science-miner.com>)

The present software is distributed under [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0). The documentation is distributed under [CC-0](https://creativecommons.org/publicdomain/zero/1.0/) license.

If you contribute to this project, you agree to share your contribution following these licenses. 

## Acknowledgements

This development is supported by the BSO3 project ("French Open Science monitor"), a "France Relance" grant from the European NextGenerationEU fundings. 

The development of *dataseer-ml* was supported by a [Sloan Foundation](https://sloan.org/) grant, see [here](https://coko.foundation/coko-receives-sloan-foundation-grant-to-build-dataseer-a-missing-piece-in-the-data-sharing-puzzle/)

