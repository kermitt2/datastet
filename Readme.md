# DataStet

[![License](http://img.shields.io/:license-apache-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)

<!--
[![Docker Hub](https://img.shields.io/docker/pulls/grobid/dataseer.svg)](https://hub.docker.com/r/grobid/dataseer/ "Docker Pulls")
[![SWH](https://archive.softwareheritage.org/badge/origin/https://github.com/kermitt2/dataseer-ml/)](https://archive.softwareheritage.org/browse/origin/?origin_url=https://github.com/kermitt2/dataseer-ml)
-->

This is originally a fork from [dataseer-ml](https://github.com/dataseer/dataseer-ml). This extended version aims at identifying every mentions of datasets in scientific documents, including implicit mentions of datasets (introduction of data created/used in the research work, but not named) and explicitly named dataset. 

Most of the datasets discussed in scientific articles are actually not named, but these data are part of the disclosed scientific work and should be shared properly to meet the [FAIR](https://en.wikipedia.org/wiki/FAIR_data) requirements. Named dataset are useful to evaluate the impact of a datasets in other research works.

Mentions of dataset are characterized automatically as _used_ or not in the research work described in the scientific document, _created_ and _shared_. 
The identified datasets are further classified in a hierarchy of dataset types, these data types being directly derived from MeSH.

The module can process a variety of scientific article formats, including mainstream publisher's native XML submission formats: PDF, TEI, JATS/NLM, ScholarOne, BMJ, Elsevier staging format, OUP, PNAS, RSC, Sage, Wiley, etc. PDF is considered as the "universal" scientific document format, but it is also the most challenging one. We use GROBID to process and structure efficiently and reliably PDF. 

The back-end service remain compatible with [dataseer-ml](https://github.com/dataseer/dataseer-ml) and the [DataSeer-Web application](https://github.com/dataseer/dataseer-web).  

Note: `.docx` format is also supported in a GROBID specific branch, but not yet merged. 

## Contact and License

Main author and contact: Patrice Lopez (<patrice.lopez@science-miner.com>)

The present software is distributed under [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0). 

The documentation is distributed under [CC-0](https://creativecommons.org/publicdomain/zero/1.0/) license.

If you contribute to this project, you agree to share your contribution following these licenses. 

## Acknowledgements

The development is supported by the BSO3 project, "France Relance" grant. 

The development of *dataseer-ml* was supported by a [Sloan Foundation](https://sloan.org/) grant, see [here](https://coko.foundation/coko-receives-sloan-foundation-grant-to-build-dataseer-a-missing-piece-in-the-data-sharing-puzzle/)


