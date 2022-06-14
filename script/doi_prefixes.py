"""
Export DOI prefixes from DataCite

- retrieve JSON files corresponding to the REST API queries: 
curl https://api.test.datacite.org/prefixes?page[size]=1000 > prefixes0.json
https://api.test.datacite.org/prefixes?page[number]=2&page[size]=1000
https://api.test.datacite.org/prefixes?page[number]=3&page[size]=1000
and so as required... 

- run the script on these json files:
python3 doi_prefixes.py prefixes0.json > doiPrefixes.txt

DOI prefix file should be under resources/lexicon/doiPrefixes.txt

Note: we do not include 10.48550 which are arXiv papers
"""

import json
import os
import argparse

def write_prefixes(json_file):
    with open(json_file, 'r') as f:
        data = json.load(f)

    if "data" in data:
        for prefix in data["data"]:
            print(prefix["id"]) 


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Grab datacite DOI prefixes")
    parser.add_argument("rest_prefixes_json_file")
    args = parser.parse_args()
    json_file = args.rest_prefixes_json_file

    write_prefixes(json_file)
