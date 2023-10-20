'''
Convert annotated document from the DataSeer application in JSON+TEI into csv data 
ready to be added to the standard dataseer classifier training data.  

Usage: See https://github.com/DataSeer/dataseer-ml#training-data-from-the-dataseer-web-application

Be sure to configure the local config.json file to include a token to access the dataseer web API 
and a list of valid curators for training case selection.

Training data in csv are produced in 3 files:
- binary.csv for binary classifier (dataset/no_dataset) with negative sampling
- reuse.csv for binary classifier (reuse/no_reuse) if reuse information is available
- multilevel.csv give the data type and data subtype for data sentences

All the annotated TEI XML files will be saved in the subdirectory corpus/ under the path given with --output

These training data file can then be used directly with DeLFT to train DL models with various architecture. 
'''

import os
import sys
import json
import csv
import gzip
import argparse
from lxml import etree
import requests

# valid curators for the set of documents annotated by modelcular connections and reviewed by Tim 
# this might need to be adapted in the future

# the following should be configured in the config file, it lists the curator identifiers valid for selecting
# the training data:
'''
annotator_identifiers = ['Curator1@molecularconnections.com', 
    'Curator2@molecularconnections.com', 
    'Curator3@molecularconnections.com',
    'Curator4@molecularconnections.com',
    'tim@dataseer.io']
'''

#annotator_identifiers = ['samanthablankers@live.ca']
#annotator_ids = ['5fad75accf1f8831ca95f290']

binary_fieldnames = ['doi', 'text', 'datatype']
reuse_fieldnames = ['doi', 'text', 'reuse']
multilevel_fieldnames = ['doi', 'text', 'datatype', 'dataSubtype', 'leafDatatype']
summary_fieldnames = ['document_doi', 'text', 'dataset_name', 'dataset_doi', 'datatype', 'dataSubtype', 'reuse', 'comment']

# counter to balance positive and negative examples to be used by the binary classifier
nb_positive_examples = 0
nb_negative_examples = 0

all_datatypes = []

documents_route = '/api/documents'
metadata_route = '/api/metadata'
tei_route = '/api/documents/:id/tei/content'
datasets_route = '/api/documents/:id?datasets=true'
accounts_route = '/api/accounts/'

# modify this parameter to modify the rate of negative sampling 
MAX_NEGATIVE_EXAMPLES_FROM_SAME_DOCUMENT = 24


def process(config, binary_csv_file, reuse_csv_file, multilevel_csv_file, summary_csv_file, dataset_section_tei_path):
    global nb_positive_examples
    global nb_negative_examples
    global all_datatypes

    # get the account ids based on the annotator account names specified in the config file
    annotator_identifiers = config["selected_annotators"]
    annotator_ids = get_account_ids(config, annotator_identifiers)

    # get the document list
    url = config["host"]
    if config["port"] > 0:
        url += ":" + config["port"]

    url_documents = url + documents_route
    print(url_documents)
    params = { "limit": 100000, "token": config["token"], "logs": True, "metadata": True }
    response = requests.get(url=url_documents, params=params)
    json_data = response.json()

    print("nb of documents:", str(len(json_data["res"])))

    nb_log_at_1 = 0

    for document in json_data["res"]:

        #document = json.loads(json_entry)
        document_id = document["_id"]

        # get the logs
        annotators = []
        #print("nb of logs:", str(len(document["logs"])))

        if "logs" in document and len(document["logs"]) == 1:
            nb_log_at_1 += 1

        valid_annotator = False

        if "logs" in document:
            for log in document["logs"]:
                #print(log["user"])
                user = log["user"]["username"]
                if user in annotator_identifiers:
                    valid_annotator = True
                    break

        # look at owner too
        if not valid_annotator and "owner" in document:
            owner = document['owner']
            if owner["_id"] in annotator_ids or owner["username"] in annotator_identifiers:
                valid_annotator = True

        # look at uploaded_by too
        '''
        if not valid_annotator and "uploaded_by" in document:
            uploader = document['uploaded_by']
            if uploader in annotator_ids:
                valid_annotator = True

        # look at watchers too
        if not valid_annotator and "watchers" in document:
            watchers = document['watchers']
            for watcher in watchers:
                if watcher in annotator_ids:
                    valid_annotator = True
                    break
        '''

        if not valid_annotator:
            continue

        # check status values in ['metadata', 'datasets', 'finish']
        '''
        status = document["status"]
        if status != 'finish':
            continue
        '''

        # as identifier we use doi or the _id as fallback
        doi = None
        if "metadata" in document:
            metadata = document["metadata"]
            if "doi" in metadata:
                doi = metadata["doi"]
                if len(doi) == 0:
                    doi = None
        if doi is None:
            doi = document_id

        # save the TEI XML document in the dedicated subdirectory
        # retrive the TEI document
        url_tei = url + tei_route.replace(":id", document_id)
        params = { "token": config["token"] }
        print(url_tei)
        response = requests.get(url=url_tei, params=params)
        raw_content = response.content
        document_tei = raw_content.decode("UTF-8")
        root = None
        if document_tei == 'null' or document_tei == None:
            print("TEI for document", doi, "is null, tei object:", document['tei'])
        else:
            destination_tei = os.path.join(dataset_section_tei_path, document_id+".tei.xml")
            with open(destination_tei, "w") as tei_file:
                tei_file.write(document_tei)

            # prepare the parsed XML document
            try:
                root = etree.fromstring(document_tei.encode('utf-8'))
            except:
                print("the parsing of the XML document failed... moving to the next entry...")
                continue

        # retrive the datasets
        url_datasets = url + datasets_route.replace(":id", document_id)
        print(url_datasets)
        params = { "token": config["token"] }
        response = requests.get(url=url_datasets, params=params)

        if response.status_code != 200:
            continue

        datasets_json_data = response.json()

        if "datasets" not in datasets_json_data["res"]:
            continue

        datasets_list = datasets_json_data["res"]["datasets"]

        if "current" in datasets_list:
            actual_datasets = datasets_list["current"]
            for dataset in actual_datasets:
                #dataset = actual_datasets[key]
                texts = []
                for sentence in dataset['sentences']: 
                    if sentence['text'] not in texts:
                        texts.append(sentence['text'])
                dataset_doi = dataset['DOI']
                dataset_name = dataset['name']
                dataset_comment = dataset['comments']

                datatype_class = 'dataset'

                # binary classifier data (datatype/no_datatype)
                # doi,text,datatype
                for text in texts:
                    if len(text) > 100:
                        binary_csv_file.writerow({'doi': doi, 'text': text, 'datatype': datatype_class})
                nb_positive_examples += len(texts)

                # the reuse information is currently not available

                # reuse classifier (true/false)
                # doi,text,reuse
                
                if "reuse" in dataset:
                    for text in texts:
                        if len(text) > 100:
                            reuse_csv_file.writerow({'doi': doi, 'text': text, 'reuse': dataset["reuse"]})

                datatype_class = dataset['dataType']

                if datatype_class not in all_datatypes:
                   all_datatypes.append(datatype_class)

                datasub_type_class = dataset['subType']
                leaf_datatype_class = ''
                
                local_id = dataset['id']

                # following https://github.com/DataSeer/dataseer-web/issues/133 we check in the TEI document if there's a subtype for this annotation
                '''
                xpath_exp = "//tei:s[@id='"+local_id+"']"
                sentences = root.xpath(xpath_exp, namespaces={'tei': 'http://www.tei-c.org/ns/1.0'})
                if len(sentences) == 1:
                    the_sentence = sentences[0]
                    local_datatype = the_sentence.get("type")
                    if local_datatype is not None:
                        pieces = local_datatype.split(":")
                        if len(pieces) == 2:
                            datasub_type_class = pieces[1]
                '''
                #else:
                #    print("Warning: sentence and possible subtype not found for", local_id, "in", document_id)
                
                # multilevel classifier data (datatype, data subtype and leaf datatype)
                #multilevel_csv_file.writerow({'doi': doi, 'text': text, 'datatype': datatype_class, 'dataSubtype': datasub_type_class, 'leafDatatype': leaf_datatype_class})
                for text in texts:
                    if len(text) > 100:
                        multilevel_csv_file.writerow({'doi': doi, 'text': text, 'datatype': datatype_class, 'dataSubtype': datasub_type_class, 'leafDatatype': leaf_datatype_class})
                        summary_csv_file.writerow({'document_doi': doi, 'text': text, 'dataset_name': dataset_name, 'dataset_doi': dataset_doi, 'datatype': datatype_class, 'dataSubtype': datasub_type_class, 'reuse': '', 'comment': dataset_comment})
        else:
            print("no current dataset associated to the document", document_id)

        # deleted dataset sentence can be used as interesting negative examples (interesting because corresponding to errors of the current model)
        if "deleted" in datasets_list:
            deleted_datasets = datasets_list["deleted"]
            for dataset in deleted_datasets:
                #dataset = actual_datasets[deleted_dataset]
                texts = []
                for sentence in dataset['sentences']: 
                    if sentence['text'] not in texts:
                        texts.append(sentence['text'])

                datatype_class = 'no_dataset'

                # binary classifier data (datatype/no_datatype)
                # doi,text,datatype
                for text in texts: 
                    if len(text) > 100:
                        binary_csv_file.writerow({'doi': doi, 'text': text, 'datatype': datatype_class})
                nb_negative_examples += len(texts)

        # in addition we can add random sentences to increase the ratio of no_dataset in the binary classifier
        # we can select these negative examples from the actual dataset sections, although reliable negative
        # classifications are also important for selecting the datset section itself

        # get random sentence without dataset information and of length at least of 150 characters
        if root != None:
            ns = {"tei": "http://www.tei-c.org/ns/1.0"}
            sentences = root.xpath('//tei:s[not(@type)]//text()', namespaces=ns)

            if nb_negative_examples < nb_positive_examples*2:
                local_addition = 0
                i = 0
                while local_addition < MAX_NEGATIVE_EXAMPLES_FROM_SAME_DOCUMENT and i < len(sentences):
                    local_text = sentences[i]
                    if len(local_text) > 150:
                        # binary classifier data (datatype/no_datatype)
                        # doi,text,datatype
                        binary_csv_file.writerow({'doi': doi, 'text': local_text, 'datatype': 'no_dataset'})
                        nb_negative_examples += 1
                        local_addition += 1
                    i += 1

    print('nb_log_at_1:', str(nb_log_at_1))

def get_account_ids(config, account_names):
    '''
    Get an account ids based on a list of account names (normally the account name is the signing email)
    '''
    # query all accounts
    url = config["host"]
    if config["port"] > 0:
        url += ":" + config["port"]

    url_accounts = url + accounts_route
    print(url_accounts)
    params = { "limit": 3000, "token": config["token"] }
    response = requests.get(url=url_accounts, params=params)
    json_data = response.json()

    #print(json_data)

    account_ids = []

    for account in json_data['res']:
        if account["username"] in account_names and not account["_id"] in account_ids:
            account_ids.append(str(account["_id"]))

    return account_ids

def _get_value(json_element):
    # get the first value of a json object, not knowing the key 
    for key in json_element.keys():
        return json_element[key]
    return None

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Converter for annotated document from the DataSeer application in JSON+TEI into training csv and TEI data ")
    parser.add_argument("--output", type=str, help="path to a directory where all the training file will be written")
    parser.add_argument("--config", default="config.json", type=str, help="path to the configuration file for DataSeer API access settings")
    args = parser.parse_args()

    config_path = args.config
    output_path = args.output

    with open(config_path, "r") as read_file:
       config = json.load(read_file)

    binary_csv = os.path.join(output_path, "binary.csv")
    binary_csv_file = open(binary_csv, mode='w')

    reuse_csv = os.path.join(output_path, "reuse.csv")
    reuse_csv_file = open(reuse_csv, mode='w')

    multilevel_csv = os.path.join(output_path, "multilevel.csv")
    multilevel_csv_file = open(multilevel_csv, mode='w')

    summary_csv = os.path.join(output_path, "extract_summary.csv")
    summary_csv_file = open(summary_csv, mode='w')

    dataset_section_tei_path = os.path.join(output_path, "corpus")

    # dataset_section_tei_path already exist we delete it
    if os.path.exists(dataset_section_tei_path) and os.path.isdir(dataset_section_tei_path):
        try:
            os.rmdir(dataset_section_tei_path)
        except OSError:
            print ("Deletion of the existing directory %s failed" % dataset_section_tei_path)
        else:
            print ("Successfully deleted the existing directory %s" % dataset_section_tei_path)

    # create directory dataset_section_tei_path
    try:
        os.mkdir(dataset_section_tei_path)
    except OSError:
        print ("Creation of the directory %s failed" % dataset_section_tei_path)
    else:
        print ("Successfully created the directory %s " % dataset_section_tei_path)

    # init the csv file writers so that we can then append the extra data
    binary_csv_writer = csv.DictWriter(binary_csv_file, fieldnames=binary_fieldnames)
    binary_csv_writer.writeheader()

    reuse_csv_writer = csv.DictWriter(reuse_csv_file, fieldnames=reuse_fieldnames)
    reuse_csv_writer.writeheader()

    multilevel_csv_writer = csv.DictWriter(multilevel_csv_file, fieldnames=multilevel_fieldnames)
    multilevel_csv_writer.writeheader()

    summary_csv_writer = csv.DictWriter(summary_csv_file, fieldnames=summary_fieldnames)
    summary_csv_writer.writeheader()

    process(config, binary_csv_writer, reuse_csv_writer, multilevel_csv_writer, summary_csv_writer, dataset_section_tei_path)
    
    binary_csv_file.close()
    reuse_csv_file.close()
    multilevel_csv_file.close()
    summary_csv_file.close()

    print("for the binary classifier: nb_positive_examples", nb_positive_examples, "/ nb_negative_examples", nb_negative_examples)
