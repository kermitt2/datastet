"""
    - convert the data type specification from the DataSeer Doku Wiki (http://wiki.dataseer.io) into a json 
    structure to be used by the DataSeer web application
    - report data types in the training data inconsistent with the DataSeer Doku Wiki
    - add data type and subtype count information into the json structure based on the actual training data
    
    Install: pip3 install -r requirements.txt

    Usage: > python3 converter.py ../resources/dataset/dataseer/csv/all-1.csv ../resources/fullDataTypes.csv 

    or to have the resulting json in the stdout
    > python3 converter.py ../resources/dataset/dataseer/csv/all-1.csv   
"""

import json
import argparse
import pandas as pd
import numpy as np
import urllib.request
from bs4 import BeautifulSoup
import re

def convert_with_pandas(input_path, output_path):
    df = pd.read_csv(input_path)    
    res = {}

    # Data type:Subtype:Sub-subtype;MESH ID;MeSH Tree No.s;Description;Best Practice;Most suitable repositories
    for index, row in df.iterrows():
        rawDataType = row['Data type:Subtype:Sub-subtype'].strip()
        if rawDataType == 'n/a':
            continue
        pieces = rawDataType.split(":")
        dataType = pieces[0]
        dataSubType = None
        dataSubSubType = None
        if len(pieces)>1:
            dataSubType = pieces[1]
            if len(pieces)>2:
                dataSubSubType = pieces[2]

        description = row['Description']
        bestPractice = row['Best Practice']
        mostSuitableRepositories = row['Most suitable repositories']

        meshId = row['MESH ID']
        meshTree= row['MeSH Tree No.s']

        if description == 'XX' or description == 'n/a':
            description = None
        if bestPractice == 'XX' or bestPractice == 'n/a':
            bestPractice = None
        if mostSuitableRepositories == 'XX' or mostSuitableRepositories == 'n/a':
            mostSuitableRepositories = None
        if meshId == 'XX' or meshId == 'n/a':
            meshId = None
        if meshTree == 'XX' or meshTree == 'n/a':
            meshTree = None    

        struct = build_struct(description, bestPractice, mostSuitableRepositories, meshId, meshTree, dataType)
        dataType = dataType.lower()
        if dataSubType is not None:
            if dataSubSubType is not None:
                if dataType in res:
                    if dataSubType in res[dataType]:
                        res[dataType][dataSubType][dataSubSubType] = struct
                    else:
                        struct2 = {}
                        struct2[dataSubSubType] = struct
                        res[dataType][dataSubType] = struct2
                else: 
                    struct3 = {}
                    struct3[dataSubSubType] = struct
                    struct2 = {}
                    struct2[dataSubType] = struct3
                    res[dataType] = struct2
            else:
                if dataType in res:
                    if dataSubType in res[dataType]:
                        struct2 = res[dataType][dataSubType]
                        for key in struct:
                            struct2[key] = struct[key]
                        res[dataType][dataSubType] = struct2
                    else:
                        res[dataType][dataSubType] = struct
                else:
                    struct2 = {}
                    struct2[dataSubType] = struct
                    res[dataType] = struct2
        else:
            if dataType in res:
                struct2 = res[dataType]
                # merging struct2 and struct
                for key in struct:
                    struct2[key] = struct[key]
                res[dataType] = struct2
            else:
                res[dataType] = struct

    with open(output_path,'w') as out:
        out.write(json.dumps(res, indent=4, sort_keys=True))


def build_struct(description=None, bestPractice=None, mostSuitableRepositories=None, meshId=None, meshTree=None, wikiUrl=None, label=None):
    struct = {}
    if description is not None:
        struct['description'] = description
    if bestPractice is not None:
        struct['best_practice'] = bestPractice    
    if mostSuitableRepositories is not None:
        struct['most_suitable_repositories'] = mostSuitableRepositories   
    if meshId is not None:
        struct['mesh_id'] = meshId   
    if meshTree is not None:
        struct['mesh_tree'] = meshTree   
    if wikiUrl is not None:
        struct['url'] = wikiUrl
    if label is not None:
        struct['label'] = label
    return struct


def load_dataseer_corpus_csv(filepath, lowerCaseClasses=False):
    """
    Load texts from the Dataseer dataset type corpus in csv format:

        doi,text,datatype,dataSubtype,leafDatatype

    Classification of the datatype follows a 3-level hierarchy, so the possible 3 classes are returned.
    dataSubtype and leafDatatype are optional

    Returns:
        tuple(numpy array, numpy array, numpy array, numpy array): 
            texts, datatype, datasubtype, leaf datatype

    """
    df = pd.read_csv(filepath)
    df = df[pd.notnull(df['text'])]
    df = df[pd.notnull(df['datatype'])]
    df.iloc[:,1].fillna('NA', inplace=True)

    texts_list = []
    for j in range(0, df.shape[0]):
        texts_list.append(df.iloc[j,1])

    datatypes = df.iloc[:,2]
    datatypes_list = datatypes.values.tolist()
    if lowerCaseClasses:
        datatypes_list = [item.lower() if isinstance(item, str) else item for item in datatypes_list]
    datatypes_list = np.asarray(datatypes_list)
    list_classes_datatypes = np.unique(datatypes_list)
    datatypes_final = normalize_classes(datatypes_list, list_classes_datatypes)

    #print(df.shape, df.shape[0], df.shape[1])

    if df.shape[1] > 3:
        datasubtypes = df.iloc[:,3]
        datasubtypes_list = datasubtypes.values.tolist()
        if lowerCaseClasses:
            datasubtypes_list = [item.lower() if isinstance(item, str) else item for item in datasubtypes_list]
        datasubtypes_list = np.asarray(datasubtypes_list)
        list_classes_datasubtypes = np.unique(datasubtypes_list)
        datasubtypes_final = normalize_classes(datasubtypes_list, list_classes_datasubtypes)

    '''
    if df.shape[1] > 4:
        leafdatatypes = df.iloc[:,4]
        leafdatatypes_list = leafdatatypes.values.tolist()
        if lowerCaseClasses:
            leafdatatypes_list = [item.lower() if isinstance(item, str) else item for item in leafdatatypes_list]
        leafdatatypes_list = np.asarray(leafdatatypes_list)
        list_classes_leafdatatypes = np.unique(leafdatatypes_list)
        leafdatatypes_final = normalize_classes(leafdatatypes_list, list_classes_leafdatatypes)
    '''

    if df.shape[1] == 3:
        return np.asarray(texts_list), datatypes_final, None, None, list_classes_datatypes.tolist(), None, None
    #elif df.shape[1] == 4 or len(leafdatatypes_list) == 0:
    else:
        return np.asarray(texts_list), datatypes_final, datasubtypes_final, None, list_classes_datatypes.tolist(), list_classes_datasubtypes.tolist(), None
    #else:
    #    return np.asarray(texts_list), datatypes_final, datasubtypes_final, leafdatatypes_final, list_classes_datatypes.tolist(), list_classes_datasubtypes.tolist(), list_classes_leafdatatypes.tolist()


def build_prior_class_distribution(distribution, trainpath, errorpath="error.txt"):
    """
    Inject count from the training data to the classification taxonomy of data types.

        - distribution is json object giving datatype descriptions without counts
        - trainpath is the path to the training data csv file

    """
    _, y_classes, y_subclasses, y_leafclasses, list_classes, list_subclasses, list_leaf_classes = load_dataseer_corpus_csv(trainpath, True)
    # lowercasing data type names to limit matching discrepancies

    #with open(jsonpath) as json_file:
    #    distribution = json.load(json_file)

    # init count everywhere
    for key1 in distribution:
        if type(distribution[key1]) is dict:
            distribution[key1]['count'] = 0
            for key2 in distribution[key1]:
                if type(distribution[key1][key2]) is dict:
                    distribution[key1][key2]['count'] = 0
                    for key3 in distribution[key1][key2]:
                        if type(distribution[key1][key2][key3]) is dict:
                            distribution[key1][key2][key3]['count'] = 0

    invalid_datatypes = []

    # inject counts in the json
    for i in range(0, len(y_classes)):
        pos_class = np.where(y_classes[i] == 1)
        pos_subclass = np.where(y_subclasses[i] == 1)
        if y_leafclasses is not None:
            pos_leafclass = np.where(y_leafclasses[i] == 1)
        #print(list_classes[pos_class[0][0]], '-', list_subclasses[pos_subclass[0][0]], '-', list_leaf_classes[pos_leafclass[0][0]])
        #print(list_classes[pos_class[0][0]], '-', list_subclasses[pos_subclass[0][0]])
        if list_classes[pos_class[0][0]] != "no_dataset":
            the_class = list_classes[pos_class[0][0]]
            if the_class in distribution:
                if list_subclasses[pos_subclass[0][0]] != "nan":
                    the_subclass = list_subclasses[pos_subclass[0][0]]
                    if the_subclass in distribution[the_class]:
                        if list_leaf_classes is not None and pos_leafclass is not None and list_leaf_classes[pos_leafclass[0][0]] != "nan":
                            the_leafclass = list_leaf_classes[pos_leafclass[0][0]]
                            if the_leafclass in distribution[the_class][the_subclass]:
                                if 'count' in distribution[the_class][the_subclass][the_leafclass]:
                                    distribution[the_class][the_subclass][the_leafclass]['count'] = distribution[the_class][the_subclass][the_leafclass]['count'] + 1
                                    distribution[the_class][the_subclass]['count'] = distribution[the_class][the_subclass]['count'] + 1
                                    distribution[the_class]['count'] = distribution[the_class]['count'] + 1
                            else:
                                error_class = the_class+":"+the_subclass+":"+the_leafclass
                                if error_class not in invalid_datatypes:
                                    invalid_datatypes.append(error_class )
                        else:
                            if 'count' in distribution[the_class][the_subclass]:
                                distribution[the_class][the_subclass]['count'] = distribution[the_class][the_subclass]['count'] + 1
                                distribution[the_class]['count'] = distribution[the_class]['count'] + 1
                    else:
                        error_class = the_class+":"+the_subclass
                        if error_class not in invalid_datatypes:
                            invalid_datatypes.append(error_class)
                else:
                    if 'count' in distribution[the_class]:
                        distribution[the_class]['count'] = distribution[the_class]['count'] + 1
            else :
                if the_class not in invalid_datatypes:
                    invalid_datatypes.append(the_class)

    with open(errorpath, 'w') as outfile:
        for datatype in invalid_datatypes:
            outfile.write("Invalid data subtype name found in training data:")
            outfile.write(datatype)
            outfile.write('\n')

    # print result in stdout
    print(json.dumps(distribution, sort_keys=False, indent=4))

def normalize_classes(y, list_classes):
    '''
        Replace string values of classes by their index in the list of classes
    '''
    def f(x):
        return np.where(list_classes == x)

    intermediate = np.array([f(xi)[0] for xi in y])
    return np.array([vectorize(xi, len(list_classes)) for xi in intermediate])


def vectorize(index, size):
    '''
    Create a numpy array of the provided size, where value at indicated index is 1, 0 otherwise 
    '''
    result = np.zeros(size)
    if index < size:
        result[index] = 1
    else:
        print("warning: index larger than vector size: ", index, size)
    return result


def wiki_capture(baseUrl="http://wiki.dataseer.io"):
    '''
    Create the initial json structure of datatypes from the wiki web site 
    '''
    res = {}

    # get the list of data types from the data_type page
    dataTypesUrl = baseUrl + "/doku.php?id=data_type&do=edit";
    fid = urllib.request.urlopen(dataTypesUrl)
    webpage = fid.read().decode('utf-8')
    content_regex = r'\[\[(.*)\]\]'
    soup = BeautifulSoup(webpage, "lxml")
    content = soup.find('textarea')

    matches = re.findall(content_regex, content.text)
    if len(matches)>0:
        for m in matches:
            segment = m.replace('data_type:', '')
            segments = segment.split('|')
            datatype_name = segments[0]
            datatype_page = segments[1]
            #print('datatype_name: ', datatype_name)
            #print('datatype_page: ', datatype_page)

            datatype_page = datatype_page.replace("*", "")
            pieces = datatype_page.split(":")
            dataType = pieces[0].strip()

            if dataType == 'MeSH':
                continue

            targetType = dataType
            dataSubType = None
            dataSubSubType = None
            if len(pieces)>1:
                dataSubType = pieces[1].strip()
                targetType = dataSubType
                if len(pieces)>2:
                    dataSubSubType = pieces[2].strip()
                    targetType = dataSubSubType

            struct = build_struct_from_page(targetType, datatype_name, baseUrl)
            dataType = dataType.lower()
            if dataSubType is not None:
                dataSubType = dataSubType.lower()
            if dataSubSubType is not None:
                dataSubSubType = dataSubSubType.lower()

            #print(struct)

            if dataSubType is not None:
                if dataSubSubType is not None:
                    if dataType in res:
                        if dataSubType in res[dataType]:
                            res[dataType][dataSubType][dataSubSubType] = struct
                        else:
                            struct2 = {}
                            struct2[dataSubSubType] = struct
                            res[dataType][dataSubType] = struct2
                    else: 
                        struct3 = {}
                        struct3[dataSubSubType] = struct
                        struct2 = {}
                        struct2[dataSubType] = struct3
                        res[dataType] = struct2
                else:
                    if dataType in res:
                        if dataSubType in res[dataType]:
                            struct2 = res[dataType][dataSubType]
                            for key in struct:
                                struct2[key] = struct[key]
                            res[dataType][dataSubType] = struct2
                        else:
                            res[dataType][dataSubType] = struct
                    else:
                        struct2 = {}
                        struct2[dataSubType] = struct
                        res[dataType] = struct2
            else:
                if dataType in res:
                    struct2 = res[dataType]
                    # merging struct2 and struct
                    for key in struct:
                        struct2[key] = struct[key]
                    res[dataType] = struct2
                else:
                    res[dataType] = struct

    #'//*[@id="wiki__text"]'

    # for each data type, get the data type page and exploit the template to get the field information
    #dataTypeUrl = baseUrl + "/doku.php?id=data_type:" + dataType +  "&do=edit";

    #struct = build_struct(description, bestPractice, mostSuitableRepositories, meshId, meshTree, dataType)

    #with open(output_path,'w') as out:
    #    out.write(json.dumps(res, indent=4, sort_keys=True))

    return res


def build_struct_from_page(datatype, datatype_page, baseUrl="http://wiki.dataseer.io"):
    dataTypeUrl = baseUrl + "/doku.php?id=data_type:" + urllib.parse.quote(datatype_page) +  "&do=edit";
    fid = urllib.request.urlopen(dataTypeUrl)
    webpage = fid.read().decode('utf-8')
    soup = BeautifulSoup(webpage, "lxml")
    content = soup.find('textarea')

    if not content:
        dataTypeUrl = baseUrl + "/doku.php?id=data_type:" + urllib.parse.quote(datatype_page) + ":" + urllib.parse.quote(datatype_page) + "&do=edit";
        fid = urllib.request.urlopen(dataTypeUrl)
        webpage = fid.read().decode('utf-8')
        soup = BeautifulSoup(webpage, "lxml")
        content = soup.find('textarea')
    
    if not content:
        print("Warning: no content found for", datatype_page)
         
    '''
    template is as follow (empty field are sometimes marked as XX): 

    ===== Name =====

    **MeSH ID:** [[https://www.ncbi.nlm.nih.gov/mesh/?term=D000792|D000792]]

    **Description:**\\ 
    blabla.

    **Best practice for sharing this type of data:**\\
    blabla

    **Most suitable repositories:**\\
    blabla

    '''

    '''
    MESH_REGEX = r'\*\*MeSH ID\:\*\* (.*)\r?\n'
    pattern_mesh = re.compile(MESH_REGEX, re.UNICODE)

    DESCRIPTION_REGEX = r'\*\*Description\:\*\*\\?\\? ?\n(.*)\r?\n'
    pattern_description = re.compile(DESCRIPTION_REGEX, re.UNICODE)

    for match in pattern_description.finditer(content.text):
        print(match)
    '''

    #print(datatype, datatype_page, dataTypeUrl)

    m_mesh = re.search(r'\*\*MeSH ID\:\*\* (.*)\r?\n', content.text)
    m_description = re.search(r'\*\*Description\:\*\*\\?\\? ?\r?\n(.*)\r?\n', content.text)
    m_bestPractice = re.search(r'\*\*Best practice for sharing this type of data\:\*\*\\?\\? ?\r?\n(.*)\r?\n', content.text)
    m_mostSuitableRepositories = re.search(r'\*\*Most suitable repositories\:\*\*\\?\\? ?\r?\n(.*)', content.text)

    description = None
    bestPractice = None, 
    mostSuitableRepositories = None
    meshId = None

    if m_mesh:
        #print(m_mesh.group(1))
        meshId = m_mesh.group(1)
        if meshId.find("|") != -1:
            meshId = meshId.split("|")[1]
            meshId = meshId.replace("]]", "")
            meshId = meshId.replace("\r", "")
        else:
            meshId = None
    if m_description:
        #print(m_description.group(1))
        description = m_description.group(1).strip()
        if description == 'XX' or description == 'n/a' or len(description) == 0:
            description = None
    if m_bestPractice:
        #print(m_bestPractice.group(1))
        bestPractice = m_bestPractice.group(1).strip()
        if bestPractice == 'XX' or bestPractice == 'n/a' or len(bestPractice) == 0:
            bestPractice = None
    if m_mostSuitableRepositories:
        #print(m_mostSuitableRepositories.group(1))
        mostSuitableRepositories = m_mostSuitableRepositories.group(1).strip()
        if mostSuitableRepositories == 'XX' or mostSuitableRepositories == 'n/a' or len(mostSuitableRepositories) == 0:
            mostSuitableRepositories = None

    return build_struct(description, bestPractice, mostSuitableRepositories, meshId, None, dataTypeUrl.replace("&do=edit",""), datatype)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Converter for data type csv files into json")
    parser.add_argument("training")
    args = parser.parse_args()
    training_file = args.training

    res = wiki_capture()
    build_prior_class_distribution(res, training_file)

    # example:
    # python3 converter.py ../resources/dataset/dataseer/csv/all-1.csv ../resources/fullDataTypes.csv 
