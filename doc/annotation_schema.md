# JSON schema for dataset mention annotations

This page presents the current JSON schema used to represent dataset mention extractions from a scholar publication or a fragment of scientific text.

## Response envelope

### Text query

The response to a text fragment-level query contains first metadata about the software version used to process the document, the date of processing and the server runtime. The list of identified dataset `mentions` are provided as a JSON array. Note that no bibliographical reference information is associated to text queries, because Grobid extracts and resolves bibliographical references at document-level. 

```json
{
    "application": "datastet",
    "version": "0.7.3-SNAPSHOT",
    "date": "2022-11-15T07:58+0000",
    "runtime": 0.496,
    "mentions": [...]
}
```

### PDF and XML document query

Similarly as text queries, the document-level information corresponds first to metadata about the software version used to process the document, the date of processing and the server runtime.

In addition in case of a PDF input, the size of the pages of the document are provided according to standard PDF representation (in particular using "abstract" PDF unit, see [here](https://grobid.readthedocs.io/en/latest/Coordinates-in-PDF/#coordinate-system-in-the-pdf) for more details). The coordinates of the annotations will be relative to these pages and their X/Y sizes.  

A MD5 hash is also included as a unique identifier of the PDF file, specific to the uploaded file binaries. 

A `references` elements is included together with the array of `mentions`, listing the extracted structured bibliographical references associated to the mentioned dataset.  

```json
    "application": "datastet",
    "version": "0.7.3-SNAPSHOT",
    "date": "2022-11-15T08:05+0000",
    "md5": "2E7BEFDEE469C6F80024FC03FD97FE18",
    "pages": [{
        "page_height": 791.0,
        "page_width": 612.0
    }, {
        "page_height": 791.0,
        "page_width": 612.0
    }, 
    ...
    {
        "page_height": 791.0,
        "page_width": 612.0
    }],
    "runtime": 4374,
    "mentions": [...],
    "references": [...]
}

```

In case an XML document is the input to be processed, representations are simplified to exclude layout coordinates and MD5 hash. 

## Mention annotation

A dataset mention is a JSON element using the following general structure, which is repeated for every mention spotted in a document: 

```json
{
    "type": "dataset",
    "wikidataId": "...",
    "wikipediaExternalRef": ...,
    "lang": "...",
    "confidence": ...,
    "dataset-name": {...},
    "dataset-implicit": {...},
    "data-device": {...},
    "data-type": {...},
    "data-repo": {...},
    "accession-number": {...},
    "url": {...},
    "context": "...",
    "references": [{
            "label": "(Jones, 1999)",
            "normalizedForm": "Jones, 1999",
            "refKey": 24
        }],
    "mentionContextAttributes": {...},
    "documentContextAttributes": {...}
}
```

The `type` here is the general type of entity and will always be `dataset` for DataStet. 

__NOTE__: fields `data-repo` and `accession-number` are not produced by current version `0.7.3-SNAPSHOT` yet, because the annotation of these fields in the trainnig data is still going on. It is expected that these fields will be support in the next version/iteration of the tool. 
In addition, the field `data-device` is currently present but trained with incompletely labeled training data, leading to very low recall. It is also expected that the next version will complete the training data annotation for this field. 

Only certain co-occurence of the fields can take place, summarized in __Table 1.__:

|                  | data-repo          | data-type          | data-device       | url                |
|------------------|--------------------|--------------------|-------------------|--------------------|
| dataset-name     | :heavy_check_mark: |                    |                   | :heavy_check_mark: |
| dataset-implicit | :heavy_check_mark: | :heavy_check_mark: | :heavy_check_mark:| :heavy_check_mark: |
| accession-number | :heavy_check_mark: |                    |                   | :heavy_check_mark: |

__Table 1. Possible field co-occurence compatibility__

__Main field description:__ A dataset mention can either be an explicitly named dataset given by the field `dataset-name`, an implicit dataset without name (`dataset-implicit`) or an `accession-number`, which is an identifier for a data entry hosted in a larger database or repository portal. __One and only one main field must present in a dataset mention.__

__Auxilairy field description:__ The field `data-repo` gives the name of the data sharing repository hosting a dataset as worded in the document (e.g. Zenodo, GitHub). `data-type` is a predicted general data type for the mentioned implicit dataset, according to 24 data types derived from MeSH descriptors. `data-device` field identifies a possible data-acquisition device used to produced an implicit dataset. Finally `url` identifies a URL which is geven for the resource in the text (which could be either URL as content text or URL as PDF annotation). __The auxiliary fields are optional__.

These fields `dataset-name`, `dataset-implicit`, `accession-number`, `data-repo`, `data-device` ad `url` and correspond to extracted chunks of information identified in the mention context. They all follow the same substructure, encoding offset information relative to the context string (`offsetStart` and `offsetEnd`) and bounding box coordinates relative to the PDF (`boundingBoxes`):

```json
    "dataset-name": {
        
    }
```

In case of a response to an XML document or a text fragment, coordinates in general are not present. 

`rawForm` give the exact strict as extracted from the source text content. `normalizedForm` is a normalized version of this extracted string, more appropriate for displaying results to users and further processing like deduplication (normalization ensures in particular a string without possible interrupting end-of-line and various possible PDF noise). 

The following fields are optional and present if a Wikidata disambiguation for the mentioned software entity has been successful:

- `wikidataId`: the Wikidata identifier (`Q` prefix string)

- `lang`: the ISO 639-1 language code to be associated to the entity

- `wikipediaExternalRef`: the page ID of software entity for the Wikipedia of the indicated language (integer value)

- `confidence`: a double between 0.0 and 1.0 indicating the confidence of the Wikidata entity resolution

See [here](https://grobid.readthedocs.io/en/latest/Coordinates-in-PDF/#coordinates-in-json-results) for information about the interpretation of the bounding box coordinates on the PDF page layout. 

`context` is the textual representation of the sentence where the software mention takes place. Offset used to identify chunk in the fields `software-name`, `version`, `publisher` and `url` are relative to this context string. 

`paragraph` (optional) is the texual representation of the complete paragraph where the software mention takes place. This extended context can be useful for applying subsequent process to a larger context than just the sentence where a mention takes place.

`references` describes the possible "reference markers" (also called "reference callouts") as extracted in the document associated to this software. There can be more than one reference markers associated to a software mention. For each reference marker, a `refKey` is used to link this reference marker to the full parsed bibliographical reference identified in the document (see next section). If the reference marker is present in the mention context, it is expressed with similar information as the extracted field attributes (so it will contain offsets and bounding box information). Otherwise, the reference marker information is propagated from other contexts. The original reference string is identified with the attribte `label` and its normalized form with the attribute `normalizedForm`.

```json
"references": [{
    "label": "(Wiederstein and Sippl, 2007)",
    "normalizedForm": "Wiederstein and Sippl, 2007",
    "refKey": 52,
    "offsetStart": 155,
    "offsetEnd": 184,
    "boundingBoxes": [{
        "p": 4,
        "x": 420.435,
        "y": 340.243,
        "w": 112.34849999999989,
        "h": 8.051699999999983
    }]
}]
```

`mentionContextAttributes` and `documentContextAttributes` follow the same scheme and provide information about the mentioned dataset in term of usage, creation and sharing based on the different dataset mention contexts in the document. Mentioned dataset are characterized with the following attributes:

 *  __used__: the mentioned dataset is used by the research work disclosed in the document
 *  __created__: the mentioned dataset is a creation of the research work disclosed in the document or the object of a contribution of the research work
 *  __shared__: dataset is claimed to be shared publicly via a sharing statement

For each of these attributes, a confidence scores in `[0,1]` and a binary class value are provided at mention-level and at document-level. Document-level values correspond to aggregation of information from all the mention contexts for the same mentioned dataset.

### Full mention example 

Here is an example of a full JSON mention object following the described scheme, with a dataset name associated to a URL and a bibliographical reference marker, for a dataset used in the described research work:

```json

```

## List of references 

Bibliographical references identified in the mention annotation are all listed in the document-level `references` array. 

The encoding is very simple and relies on GROBID TEI results. For every involved bibliographical references:

- an numerical element `refKey` is used as local identifier to cross-reference the reference citation in the mention context and the parsed reference

- the parsed bibliographical reference is previded in a `tei` element as standard TEI XML encoding. 

The XML string can then be retrieved from the JSON result and parsed with the appropriate XML parser. 

```json
"references": [{
        "refKey": 24,
        "tei": "<biblStruct xml:id=\"b24\">\n\t<analytic>\n\t\t<title level=\"a\" type=\"main\">Protein secondary structure prediction based on position-specific scoring matrices 1 1Edited by G. Von Heijne</title>\n\t\t<author>\n\t\t\t<persName><forename type=\"first\">David</forename><forename type=\"middle\">T</forename><surname>Jones</surname></persName>\n\t\t</author>\n\t\t<idno type=\"DOI\">10.1006/jmbi.1999.3091</idno>\n\t</analytic>\n\t<monogr>\n\t\t<title level=\"j\">Journal of Molecular Biology</title>\n\t\t<title level=\"j\" type=\"abbrev\">Journal of Molecular Biology</title>\n\t\t<idno type=\"ISSN\">0022-2836</idno>\n\t\t<imprint>\n\t\t\t<biblScope unit=\"volume\">292</biblScope>\n\t\t\t<biblScope unit=\"issue\">2</biblScope>\n\t\t\t<biblScope unit=\"page\" from=\"195\" to=\"202\" />\n\t\t\t<date type=\"published\" when=\"1999-09\">1999</date>\n\t\t\t<publisher>Elsevier BV</publisher>\n\t\t</imprint>\n\t</monogr>\n</biblStruct>\n"
    }, {
        "refKey": 44,
        "tei": "<biblStruct xml:id=\"b44\">\n\t<analytic>\n\t\t<title level=\"a\" type=\"main\">Comparative protein modelling by satisfaction of spatial restraints</title>\n\t\t<author>\n\t\t\t<persName><forename type=\"first\">A</forename><surname>Sali</surname></persName>\n\t\t</author>\n\t\t<author>\n\t\t\t<persName><forename type=\"first\">T</forename><surname>Blundell</surname></persName>\n\t\t</author>\n\t</analytic>\n\t<monogr>\n\t\t<title level=\"j\">Journal of Molecular Biology</title>\n\t\t<imprint>\n\t\t\t<biblScope unit=\"volume\">234</biblScope>\n\t\t\t<biblScope unit=\"page\" from=\"779\" to=\"815\" />\n\t\t\t<date type=\"published\" when=\"1993\">1993</date>\n\t\t</imprint>\n\t</monogr>\n</biblStruct>\n"
    }, {
        "refKey": 52,
        "tei": "<biblStruct xml:id=\"b52\">\n\t<analytic>\n\t\t<title level=\"a\" type=\"main\">ProSA-web: interactive web service for the recognition of errors in three-dimensional structures of proteins</title>\n\t\t<author>\n\t\t\t<persName><forename type=\"first\">B</forename><surname>Wei</surname></persName>\n\t\t</author>\n\t\t<author>\n\t\t\t<persName><forename type=\"first\">R</forename><forename type=\"middle\">L</forename><surname>Jing</surname></persName>\n\t\t</author>\n\t\t<author>\n\t\t\t<persName><forename type=\"first\">C</forename><forename type=\"middle\">S</forename><surname>Wang</surname></persName>\n\t\t</author>\n\t\t<author>\n\t\t\t<persName><forename type=\"first\">Xp ;</forename><surname>Chang</surname></persName>\n\t\t</author>\n\t\t<author>\n\t\t\t<persName><forename type=\"first\">M</forename><surname>Wiederstein</surname></persName>\n\t\t</author>\n\t\t<author>\n\t\t\t<persName><forename type=\"first\">M</forename><forename type=\"middle\">J</forename><surname>Sippl</surname></persName>\n\t\t</author>\n\t</analytic>\n\t<monogr>\n\t\t<title level=\"j\">Scientia Agricola Sinica</title>\n\t\t<imprint>\n\t\t\t<biblScope unit=\"volume\">39</biblScope>\n\t\t\t<biblScope unit=\"page\" from=\"407\" to=\"410\" />\n\t\t\t<date type=\"published\" when=\"2006\">2006. 2007</date>\n\t\t</imprint>\n\t</monogr>\n\t<note>Nucleic Acids Research</note>\n</biblStruct>\n"
    }
]
```


