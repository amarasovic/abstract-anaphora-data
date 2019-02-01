from __future__ import unicode_literals


from nltk.tokenize import sent_tokenize
from nltk.tokenize import word_tokenize
from nltk.parse import stanford
from nltk import pos_tag

from cort.core import corpora
from collections import defaultdict
import numpy as np
import codecs
import random
import copy
import json
import sys
import os

import spacy
import string
nlp = spacy.load('en')

random.seed(24)

stanford_parser_path = '/path_to_stanford_parser/stanford-parser-full-2017-06-09/stanford-parser.jar'
os.environ['STANFORD_PARSER'] = stanford_parser_path

stanford_models_path = '/path_to_stanford_parser/stanford-parser-full-2017-06-09/stanford-parser-3.8.0-models.jar'
os.environ['STANFORD_MODELS'] = stanford_models_path

parser = stanford.StanfordParser(model_path="edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz")


def load_asn_with_candidates():
    with open('asn_data/asn_parsed_candidates.json') as f:
        return json.load(f)


def check_sent(filler, sen, maxlen=40, minlen=3):
    if len(sen.split(' ')) > maxlen or len(sen.split(' ')) < minlen:
        return filler
    else:
        return sen


def get_all_subtree_leaves_plus_nodes(tree, label=None):
    lss = []
    nodes = []
    if not label:
        for tr in tree.subtrees():
            lss.append(tr.leaves())
            nodes.append(tr.label())
    else:
        for tr in tree.subtree(filter=lambda x: x.node == label):
            lss.append(tr.leaves())
            nodes.append(tr.label())
    return lss, nodes


def produce_asn_json(ctx_len=4):
    print 'started making initial ASN json...'
    print 'loading the asn json...'

    # obtain REAL_JSON_Copyright_protected_ASN_corpus_with_text.json from Varada Kolhatkar ---> http://www.cs.toronto.edu/~varada/VaradaHomePage/Home.html
    with open('asn_data/REAL_JSON_Copyright_protected_ASN_corpus_with_text.json') as asn_dict:
        asn_dict = json.load(asn_dict)
    print 'done'

    asn_dict_new = []

    filler = "This is not a real sentence."
    stats = [0]*6

    'start iterating over the asn json...'
    for o, d in enumerate(asn_dict):
        print o
        text = d['text']
        d['anaphor_phrase'] = d['anaphor']
        d['anaphor_head'] = d['anaphor_phrase'].split(' ')[1]
        del d['anaphor']

        sents = sent_tokenize(text)

        antecs_idx = -1# index of the sentence in which the antecedent occurs in

        for i, sen in enumerate(sents):
            translate_table = dict((ord(char), None) for char in string.punctuation)
            temp1 = sent_tokenize(d['sentence'])[0].translate(translate_table)

            diff = set(word_tokenize(temp1)).difference(set(word_tokenize(sen.translate(translate_table))))

            if len(diff) <= 3:
                antecs_idx = i

        # if the antecedent sentence is not found this example is useless
        if antecs_idx == -1:
            # print 'the antecedent sentence was not found!\n'
            stats[0] += 1
            continue

        anaphs_idx = -1 # index of the sentence in which the anaphor occurs in
        phrase_indices = [ids for ids, sent in enumerate(sents) if d['anaphor_phrase'] in sent]
        prev_context = []

        for i, sen in enumerate(sents):
            if d['anaphor_phrase'] in sen:
                # hypotetically the anaphoric phrase might occur more than once in the text
                # in that case, choose the anaphoric sentence which is the closes to the sentence with the antecedent
                if len(phrase_indices) == 1:
                    anaphs_idx = i

                if len(phrase_indices) > 1:
                    distances = [j-antecs_idx for j in phrase_indices]
                    min_ind = np.argmin(np.asarray(distances))
                    if phrase_indices[min_ind] == i:
                        anaphs_idx = i

                #prev_context.append(sen)

                if i >= ctx_len:
                    for j in range(1, ctx_len+1):
                        prev_context.append(sents[i-j])
                else:
                    diff = ctx_len - i
                    prev_context = [filler]*diff
                    for j in range(1, i+1):
                        prev_context.append(sents[i-j])
                    prev_context += [filler] * (ctx_len - i)

                sent = word_tokenize(sen)
                anaph_id = sent.index(d['anaphor_head'])
                sent.insert(anaph_id-1, '-X-')
                sent.insert(anaph_id+2, '-X-')
                d['anaphor_sentence'] = ' '.join(sent)
                break

        if anaphs_idx == -1:
            #print 'the anaphoric sentence was not found!\n'
            stats[1] += 1
            continue

        d['antecedent_distances'] = [anaphs_idx - antecs_idx]

        k = 1
        alist = []
        ascores = []
        first = None
        first_score = None
        empty_str = 'EMPTYEMPTY'
        while 'antecedent' + str(k) in d:
            if ':1.0' in d['antecedent' + str(k)]:
                if d['antecedent' + str(k)].split(':1.0')[0] != empty_str:
                    alist.append(d['antecedent' + str(k)].split(':1.0')[0])
                    ascores.append('1.0')
            else:
                if float(d['antecedent' + str(k)].split(':0.')[1]) >= 500 and\
                        d['antecedent' + str(k)].split(':0.')[0] != empty_str:
                    alist.append(d['antecedent' + str(k)].split(':0.')[0])
                    ascores.append('0.' + d['antecedent' + str(k)].split(':0.')[1])

                if k == 1 and d['antecedent1'].split(':0.')[0] != empty_str:
                    first = d['antecedent' + str(k)].split(':0.')[0]
                    first_score = '0.' + d['antecedent' + str(k)].split(':0.')[1]

            del d['antecedent' + str(k)]
            k += 1

        # if all antecedents are "low-scoring":
        # take the first scoring antecedent (no matter what the score was)
        if not alist:
            if first:
                alist.append(first)
                ascores.append(first_score)
                #print 'had to take a low score antecedent!\n'
                stats[3] += 1

        if not alist:
            #print 'all antecedents were EMPTYEMPTY\n'
            stats[4] += 1
            continue

        # construct negative candidates = all sub-consitutents of sentence with the antecedent
        leavess, nodes = get_all_subtree_leaves_plus_nodes(list(parser.raw_parse(d["sentence"]))[0])
        candidates = [" ".join(c) for c in leavess]  # list(set(candidates))
        candidates_nodes = nodes

        # find the constituent tag label of the antecedent (if it is a sub-constitutent)
        anodes = [None]*len(alist)
        done = [False]*len(alist)

        indices = []
        for aid, a in enumerate(alist):
            for i, (c, n) in enumerate(zip(candidates, candidates_nodes)):
                if not done[aid]:
                    translate_table = dict((ord(char), None) for char in string.punctuation)
                    temp1 = a.translate(translate_table).replace(' s ', 's ')
                    temp2 = c.translate(translate_table).replace(' s ', 's ')
                    #print temp1 + ' <-------> ' + temp2
                    sym_difference = list(set(word_tokenize(temp1)) ^ set(word_tokenize(temp2)))

                    if len(sym_difference) == 0:
                        anodes[aid] = n
                        done[aid] = True
                        indices.append(i)

            for i, (c, n) in enumerate(zip(candidates, candidates_nodes)):
                if not done[aid]:
                    translate_table = dict((ord(char), None) for char in string.punctuation)
                    temp1 = a.translate(translate_table).replace(' s ', 's ')
                    temp2 = c.translate(translate_table).replace(' s ', 's ')
                    #print temp1 + ' <-------> ' + temp2
                    sym_difference = list(set(word_tokenize(temp1)) ^ set(word_tokenize(temp2)))

                    if not done[aid]:
                        if len(sym_difference) == 1:
                            anodes[aid] = n
                            done[aid] = True
                            indices.append(i)

        for i, n in enumerate(anodes):
            if not n:
                #print 'an antecedent is not a syntactic constituent!\n'
                anodes[i] = 'S'
                stats[5] += 1

        asorted = [a for _, a in sorted(zip(ascores, alist), reverse=True)]
        d['antecedents'] = asorted
        d['antecedent_scores'] = sorted(ascores, reverse=True)
        d['antecedent_nodes'] = anodes

        # remove antecedents from list of negative candidates
        candidates_filter = [c for i, c in enumerate(candidates) if i not in indices]
        candidates_nodes_filter = [c for i, c in enumerate(candidates_nodes) if i not in indices]

        d['candidates'] = candidates_filter
        d['candidates_nodes'] = candidates_nodes_filter

        prev_context_filter = [x for x in prev_context if x != 'This is not a real sentence.']
        d['prev_context'] = prev_context_filter[::-1]
        d['antec_sentence'] = d['sentence']
        del d['sentence']

        asn_dict_new.append(d)

    with open('aar_jsons/asn_init.json', 'w') as fp:
        json.dump(asn_dict_new, fp, indent=4, sort_keys=True)

    print 'number of examples for which the antecedent sentence was not found: ' + str(stats[0])
    print 'number of examples for which the anaphoric sentence not found: ' + str(stats[1])
    print 'number of examples with a low score antencent: ' + str(stats[3])
    print 'number of examples for which all antecedents were EMPTYEMPTY: ' + str(stats[4])
    print 'number of antecedents that are not syntactic constituents: ' + str(stats[5])


def _produce_conll_json(corpus, mode, ctx_len=4):
    print 'started to make an initial conll json for ' + mode
    json_list = []
    for doc in corpus:
        mentions = defaultdict(list)
        sentence_spans = doc.sentence_spans
        tokens = doc.tokens
        in_sentence_ids = doc.in_sentence_ids

        for i, mention in enumerate(doc.annotated_mentions):
            pronouns = [['This'], ['this'], ['That'], ['that'], ['It'], ['it']]
            if mention.attributes['tokens'] in pronouns or mention.attributes['type'] == 'VRB':
                mentions[mention.attributes['annotated_set_id']].append(mention)

        for sid, mlist in mentions.iteritems():
            for i, m in enumerate(mlist):
                if m.attributes['type'] == 'VRB':
                    if i < len(mlist) - 1:
                        if mlist[i + 1].attributes['tokens'] in pronouns:
                            mention_dict = {}

                            mention_dict['document'] = doc.identifier

                            distance = mlist[i + 1].attributes['sentence_id'] - m.attributes['sentence_id']
                            mention_dict['antecedent_distances'] = [distance]

                            anaph_span = mlist[i + 1].span
                            anaphor_sent_position = (in_sentence_ids[anaph_span.begin], in_sentence_ids[anaph_span.end])
                            anaph_sent_token = tokens[sentence_spans[mlist[i + 1].attributes['sentence_id']].begin:
                                                      sentence_spans[mlist[i + 1].attributes['sentence_id']].end]
                            anaph_sent_token.insert(anaphor_sent_position[0], '-X-')
                            anaph_sent_token.insert(anaphor_sent_position[0] + 2, '-X-')
                            mention_dict['anaphor_sentence'] = ' '.join(anaph_sent_token)

                            anaph = mlist[i + 1].attributes['tokens']
                            mention_dict['anaphor_head'] = anaph[0]

                            antec_sent_token = tokens[sentence_spans[m.attributes['sentence_id']].begin:sentence_spans[
                                m.attributes['sentence_id']].end]
                            antec_sent = ' '.join(antec_sent_token)
                            mention_dict['antec_sentence'] = antec_sent

                            antec_token = m.attributes['parse_tree'].leaves()
                            antec = ' '.join(antec_token)
                            #mention_dict['antecedents'] = [antec]
                            #mention_dict['antecedent_nodes'] = ['VP']

                            # construct negative candidates = all sub-consitutents of sentence with the antecedent
                            leavess, nodes = get_all_subtree_leaves_plus_nodes(list(parser.raw_parse(antec_sent))[0])
                            candidates = [" ".join(c) for c in leavess]  # list(set(candidates))
                            candidates_nodes = nodes

                            # find the constituent tag label of the antecedent (if it is a sub-constitutent)
                            alist = [antec]
                            anodes = [None]
                            done = [False]

                            indices = []
                            for aid, a in enumerate(alist):
                                for j, (c, n) in enumerate(zip(candidates, candidates_nodes)):
                                    if not done[aid]:
                                        translate_table = dict((ord(char), None) for char in string.punctuation)
                                        temp1 = a.translate(translate_table).replace(' s ', 's ')
                                        temp2 = c.translate(translate_table).replace(' s ', 's ')
                                        # print temp1 + ' <-------> ' + temp2
                                        sym_difference = list(set(word_tokenize(temp1)) ^ set(word_tokenize(temp2)))

                                        if len(sym_difference) == 0:
                                            anodes[aid] = n
                                            done[aid] = True
                                            indices.append(j)

                                for j, (c, n) in enumerate(zip(candidates, candidates_nodes)):
                                    if not done[aid]:
                                        translate_table = dict((ord(char), None) for char in string.punctuation)
                                        temp1 = a.translate(translate_table).replace(' s ', 's ')
                                        temp2 = c.translate(translate_table).replace(' s ', 's ')
                                        # print temp1 + ' <-------> ' + temp2
                                        sym_difference = list(set(word_tokenize(temp1)) ^ set(word_tokenize(temp2)))

                                        if not done[aid]:
                                            if len(sym_difference) == 1:
                                                anodes[aid] = n
                                                done[aid] = True
                                                indices.append(j)

                            for j, n in enumerate(anodes):
                                if not n:
                                    # print 'an antecedent is not a syntactic constituent!\n'
                                    anodes[j] = 'VP'
                            mention_dict['antecedents'] = [antec]
                            mention_dict['antecedent_nodes'] = anodes

                            # remove antecedents from list of negative candidates
                            candidates_filter = [c for i, c in enumerate(candidates) if i not in indices]
                            candidates_nodes_filter = [c for i, c in enumerate(candidates_nodes) if
                                                       i not in indices]

                            mention_dict['candidates'] = candidates_filter
                            mention_dict['candidates_nodes'] = candidates_nodes_filter
                            ###############

                            prev_context = []
                            anaph_sent_id = mlist[i + 1].attributes['sentence_id']
                            for j in range(1, ctx_len + 1):
                                if anaph_sent_id - j >= 0:
                                    sent = tokens[sentence_spans[anaph_sent_id-j].begin:sentence_spans[anaph_sent_id-j].end]
                                    sent_str = ' '.join(sent)
                                    prev_context.append(sent_str)
                                else:
                                    prev_context.append([])
                            mention_dict['prev_context'] = prev_context[::-1]
                            json_list.append(mention_dict)
    with open('aar_jsons/conll_' + mode + '_init.json', 'w') as fp:
        json.dump(json_list, fp, indent=4, sort_keys=True)


def produce_conll_json(mode):
    corpus = corpora.Corpus.from_file(mode, codecs.open('conll-12/' + mode + '.english.v4_auto_conll',
                                                        "r", "utf-8"))
    _produce_conll_json(corpus, mode)



def separate_positive_negative(data_name):
    print 'separating positves and negatives for ' + data_name
    with open('aar_jsons/' + data_name + '_init.json', 'r') as data_dict:
        data = json.load(data_dict)
    data_new = []
    for did, d in enumerate(data):
        print did+1
        # remove the antecedent from the negative candidate list
        # remove duplicates from the negative candidates list
        alist = d['antecedents']
        candidates = d['candidates']
        candidates_nodes = d['candidates_nodes']
        indices = []
        for aid, a in enumerate(alist):
            for i, (c, n) in enumerate(zip(candidates, candidates_nodes)):
                translate_table = dict((ord(char), None) for char in string.punctuation)
                temp1 = a.translate(translate_table).replace(' s ', 's ')
                temp2 = c.translate(translate_table).replace(' s ', 's ')
                sym_difference = list(set(word_tokenize(temp1)) ^ set(word_tokenize(temp2)))

                if len(sym_difference) == 0:
                    indices.append(i)

        candidates_filter = []
        candidates_nodes_filter = []
        for i, (n, t) in enumerate(zip(candidates, candidates_nodes)):
            if i not in indices and n not in candidates_filter:
                candidates_filter.append(n)
                candidates_nodes_filter.append(t)
        d['candidates'] = candidates_filter
        d['candidates_nodes'] = candidates_nodes_filter

        if data_name in ['wsj', 'nyt']:
            dist = d['antecedent_distances'][0]
            d['candidates_'+str(dist)] = candidates_filter
            d['candidates_nodes_'+str(dist)] = candidates_nodes_filter
        data_new.append(d)

    with open('aar_jsons/' + data_name + '_temp1.json', 'w') as fp:
        json.dump(data_new, fp, indent=4, sort_keys=True)


def add_verb_feature(data_name):
    print 'started adding the verb feature for ' + data_name
    with open('aar_jsons/' + data_name + '_temp1.json', 'r') as data_dict:
        data = json.load(data_dict)
    data_new = []
    for did, d in enumerate(data):
        print did+1
        verb = None
        relation = None
        ahead = d['anaphor_head'] 

        nlp = spacy.load('en')
        anno = nlp(d['anaphor_sentence'].replace('-X-', ''))

        for chunk in anno.noun_chunks:
            #print (chunk.text, chunk.root.text, chunk.root.dep_, chunk.root.head.text)
            anno_tuple = (chunk.text, chunk.root.text, chunk.root.dep_, chunk.root.head.text, chunk.root.head.pos_)
            if anno_tuple[1] == ahead and anno_tuple[4] == 'VERB':
                verb = chunk.root.head.text
                relation = anno_tuple[2]

        if not verb:
            triplets = []
            for token in anno:
                triplet = ((token.text, token.pos_), token.dep_, (token.head.text, token.head.pos_))
                triplets.append(triplet)

            aid = 0
            for k, triplet in enumerate(triplets):
                if triplet[0][0] == ahead:
                    aid = k

            while aid >= 0 and triplets[aid][2][1] != 'VERB':
                if triplets[aid][1] == 'ROOT':
                    break
                aid -= 1
            if triplets[aid][1] != 'ROOT':
                verb = triplets[aid][2][0]
                relation = triplets[aid][1]

            if relation in ['prep', 'advmod']:
                asent_str = d['anaphor_sentence']

                asent = word_tokenize(asent_str.replace('-X-', '-X- '))
                aid = asent.index('-X-')
                pos_asent = pos_tag(asent)

                verb_not_find = True
                verb_pos_list = ['VB', 'VBD', 'VBG', 'VBN', 'VBP', 'VBZ']
                i = 0
                verb = None
                while verb_not_find and i < 3:
                    i += 1
                    if aid - i > 0 and pos_asent[aid - i][1] in verb_pos_list:
                        verb = pos_asent[aid - i][0]
                        verb_not_find = False

                    if aid - i < 0:
                        verb_not_find = False
                if not verb:
                    relation = None

        d['verb'] = verb
        d['verb_deplabel'] = relation

        data_new.append(d)

    with open('aar_jsons/' + data_name + '_temp2.json', 'w') as fp:
        json.dump(data_new, fp, indent=4, sort_keys=True)


def split_asn():
    sn = ['fact', 'reason', 'decision', 'issue', 'question', 'possibility']
    sn_dict = defaultdict(list)
    with open('aar_jsons/asn_temp2.json', 'r') as data_dict:
        data = json.load(data_dict)
    for d in data:
        sn_dict[d['anaphor_head']].append(d)

    train_data = []
    dev_data = []
    for n in sn:
        sn_dict[n] = random.sample(sn_dict[n], len(sn_dict[n]))
        if n != 'decision':
            dev_data.extend(sn_dict[n][:60])
            train_data.extend(sn_dict[n][60:])
    test_data = sn_dict['decision']

    with open('aar_jsons/asn_train_temp2.json', 'w') as fp:
        json.dump(train_data, fp, indent=4, sort_keys=True)

    with open('aar_jsons/asn_dev_temp2.json', 'w') as fp:
        json.dump(dev_data, fp, indent=4, sort_keys=True)

    with open('aar_jsons/asn_test_temp2.json', 'w') as fp:
        json.dump(test_data, fp, indent=4, sort_keys=True)


def arrau_merge():
    arrau_data = []
    for mode in ['train', 'test', 'dev']:
        with open('aar_jsons/arrau_'+mode+'.json', 'r') as mode_file:
            mode_data = json.load(mode_file)

        for item in mode_data:
            p = item['prev_context'][::-1]
            item['prev_context'] = p
            arrau_data.append(item)
    with open('aar_jsons/arrau_init.json', 'w') as fp:
        json.dump(arrau_data, fp, indent=4, sort_keys=True)


def prepare_silver(data_name):
    print '\nstarted XXX ' + data_name + '\n'
    fname = 'aar_jsons/' + data_name + '_init.json'
    with open(fname, "r") as f:
        data_dict = json.load(f)

    with open(fname, "r") as f:
        data_list = json.load(f)

    with open('data_stats/stats4train.json') as fp:
        stats4train = json.load(fp)
    distance_counts = stats4train['distance_list']

    counts = np.asarray(np.bincount(distance_counts), dtype=float)
    probabilities = counts / counts.sum()
    distances = [0, 1, 2, 3, 4]
    distance_distr = np.random.choice(distances, len(data_list), p=probabilities).tolist()

    data_dict_new = []
    say_count = 0
    for s, sample in enumerate(data_dict):
        print str(s+1) + '/' + str(len(data_list))

        del sample['candidates']
        del sample['candidates_nodes']

        del sample['candidates_0']
        del sample['candidates_nodes_0']

        anaphs = sample['anaphor_sentence'].replace('-X-', '')
        if data_name in ['nyt', 'wsj'] and len(word_tokenize(anaphs)) < 15:
            continue
        if data_name == 'nyt' and sample['vp'].lower() in ['said', 'say', 'says', 'saying']:
            continue
        if data_name == 'wsj' and sample['vp'].lower() in ['said', 'say', 'says', 'saying']:
            if say_count > 100:
                continue
            else:
                say_count += 1

        leavess, nodes = get_all_subtree_leaves_plus_nodes(list(parser.raw_parse(anaphs))[0])
        sample['candidates_0'] = [' '.join(c) for c in leavess]
        sample["candidates_nodes_0"] = nodes

        artificial_antecedent = sample['artificial_antecedent']
        # negative candidates are all sub-constituents of the antecedent
        leavess, nodes = get_all_subtree_leaves_plus_nodes(list(parser.raw_parse(artificial_antecedent))[0])
        sample["candidates"] = [' '.join(c) for c in leavess]
        sample["candidates_nodes"] = nodes

        random_dist = distance_distr.pop()
        sample['antecedent_distances'] = [random_dist]
        sample['candidates_' + str(random_dist)] = [' '.join(c) for c in leavess]
        sample["candidates_nodes_" + str(random_dist)] = nodes

        data_dict_new.append(sample)

    with open('aar_jsons/' + data_name + '_init.json', 'w') as fp:
        json.dump(data_dict_new, fp, indent=4, sort_keys=True)


def extend_positives_list(plist, ptag_list, pdist_list, nlist, ntag_list):
    '''
    Extend the list of antecedents with negative candidates that differ in one word and any number of punctuation symbols.
    Remove a negative candidate that:
        - contains the antecedent
        - that starts with the head of the sbar constituent (e.g. that X)
        - that contains the word before the anaphor replacement (e.g. said X)
    :param plist: list of positive candidate strings
    :param ptag_list: list of positive candidate constituent tag labels
    :param nlist: list of negative candidate strings
    :param ntag_list: list of negative candidate constituent tag labels
    :param anaphor_derived_from: the head of the sbar constituent
    :param anaph_sent: string of the anaphoric sentence
    :return:
    '''
    plist_temp = copy.deepcopy(plist)
    plist_tag_temp = copy.deepcopy(ptag_list)
    plist_dist_temp = copy.deepcopy(pdist_list)
    indices = []

    assert len(plist_temp) == len(plist_tag_temp) == len(plist_dist_temp)

    for i, (negative, negative_tag) in enumerate(zip(nlist, ntag_list)):
        for j, (positive, positive_tag) in enumerate(zip(plist_temp, plist_tag_temp)):
            sym_difference = list(set(word_tokenize(positive)) ^ set(word_tokenize(negative)))
            punctuation = list(string.punctuation)
            intersection_strip = [s for s in sym_difference if s not in punctuation]

            if len(intersection_strip) <= 1:
                indices.append(i)
                plist.append(negative)
                ptag_list.append(negative_tag)
                pdist_list.append(plist_dist_temp[j])

    nlist_final = [x for i, x in enumerate(nlist) if i not in indices]
    ntag_list_final = [x for i, x in enumerate(ntag_list) if i not in indices]

    '''
    print '------'
    print plist
    print '\n'.join(nlist_final)
    print list(set(plist) & set(nlist_final))
    '''

    assert len(list(set(plist) & set(nlist_final))) == 0

    return plist, ptag_list, pdist_list, nlist_final, ntag_list_final


def move2antecedent(data_name):
    '''
    Extend the list of antecedents with negative candidates that differ in one word and any number of punctuation symbols.
    :param data_dic: data dictionary
    :return: new data dictionary
    '''

    print '\nstarted extending the antecedent list for ' + data_name + '\n'

    with open('aar_jsons/' + data_name + '_temp2.json', "r") as f:
        data_dic = json.load(f)
    assert data_dic

    data_dict_new = []
    for s, sample in enumerate(data_dic):
        print s

        plist_ext, ptag_list_ext, pdist_ext, nlist_ext, ntag_list_ext = extend_positives_list(sample['antecedents'],
                                                                                              sample['antecedent_nodes'],
                                                                                              sample['antecedent_distances'],
                                                                                              sample['candidates'],
                                                                                              sample['candidates_nodes'])

        sample['antecedents'] = plist_ext
        sample['antecedent_nodes'] = ptag_list_ext
        sample['antecedent_distances'] = pdist_ext

        sample['candidates'] = nlist_ext
        sample['candidates_nodes'] = ntag_list_ext

        antec_dists = copy.deepcopy(sample['antecedent_distances'])
        for d in antec_dists:
            if d < 5:
                if len(set(antec_dists)) == 1:
                    sample['candidates_'+str(d)] = nlist_ext
                    sample['candidates_nodes_'+str(d)] = ntag_list_ext
                if len(set(antec_dists)) > 1:
                    prev_context = sample['prev_context'][::-1]

                    if d != 0:
                        sent_str = prev_context[d-1]
                    if d == 0:
                        sent_str = sample['anaphor_sentence']

                    leavess, nodes = get_all_subtree_leaves_plus_nodes(list(parser.raw_parse(sent_str))[0])
                    candidates = [' '.join(c) for c in leavess]
                    _, _, _, nlist_ext, ntag_list_ext = extend_positives_list(sample['antecedents'],
                                                                              sample['antecedent_nodes'],
                                                                              sample['antecedent_distances'],
                                                                              candidates,
                                                                              nodes)
                    sample["candidates_" + str(d)] = nlist_ext
                    sample["candidates_nodes_" + str(d)] = ntag_list_ext
        data_dict_new.append(sample)

    with open('aar_jsons/' + data_name + '_temp3.json', 'w') as fp:
        json.dump(data_dict_new, fp, indent=4, sort_keys=True)


def extend_json(data_name, ctx_len=4):
    print '\nstarted adding candidates from the wider context for ' + data_name + '\n'
    with open('aar_jsons/' + data_name + '_temp3.json', "r") as f:
        data_dict = json.load(f)

    data_dict_new = []
    for s, sample in enumerate(data_dict):
        print s

        distances_set = list(set(sample['antecedent_distances']))

        prev_context = sample['prev_context'][::-1]

        if len(prev_context) < ctx_len:
            diff = ctx_len - len(prev_context)
            for _ in range(diff):
                prev_context.append('')

        prev_context.insert(0, sample['anaphor_sentence'].replace('-X- ', ''))

        for j in range(ctx_len + 1):
            if not prev_context[j]:
                sample["candidates_" + str(j)] = []
                sample["candidates_nodes_" + str(j)] = []
            else:
                if j not in distances_set and "candidates_" + str(j) not in sample:
                    sent_str = prev_context[j]
                    leavess, nodes = get_all_subtree_leaves_plus_nodes(list(parser.raw_parse(sent_str))[0])
                    sample["candidates_" + str(j)] = [' '.join(c) for c in leavess]
                    sample["candidates_nodes_" + str(j)] = nodes
        data_dict_new.append(sample)
    with open('aar_jsons/' + data_name + '_final.json', 'w') as fp:
        json.dump(data_dict_new, fp, indent=4, sort_keys=True)


def remove_arrau_candidates():
    with open('aar_jsons/arrau_temp2.json', "r") as f:
        data_dict = json.load(f)

    data_dict_new = []
    for s, sample in enumerate(data_dict):
        for k in range(5):
            if 'candidates_'+str(k) in sample:
                del sample['candidates_'+str(k)]
        data_dict_new.append(sample)
    with open('aar_jsons/arrau_temp2.json', 'w') as fp:
        json.dump(data_dict_new, fp, indent=4, sort_keys=True)


def main(flag):
    if flag == 'asn':
        produce_asn_json()
        separate_positive_negative('asn')
        add_verb_feature('asn')
        split_asn()
        for mode in ['train', 'dev', 'test']:
            dname = 'asn_' + mode
            move2antecedent(dname)
            extend_json(dname)

    if flag == 'conll':
        for mode in ['train']:
            produce_conll_json(mode)
            dname = 'conll_' + mode
            separate_positive_negative(dname)
            add_verb_feature(dname)
            move2antecedent(dname)
            extend_json(dname)


    if flag == 'arrau':
        dname = 'arrau'
        separate_positive_negative(dname)
        add_verb_feature(dname)
        remove_arrau_candidates()
        move2antecedent(dname)
        extend_json(dname)

    if flag in ['wsj', 'nyt']:
        prepare_silver(flag)
        separate_positive_negative(flag)


if __name__ == '__main__':
    main(sys.argv[1])


