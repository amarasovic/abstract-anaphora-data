from nltk.tokenize import word_tokenize
import string
import json
import sys
import copy


def filter_by_antecedent_and_anaphor(dat):
    '''
    Filter exmaples:
        - anaphor type = direct
        - sbar head = whether and antecedents of the format "X or Y"
        - antecedent starts with "to"
        - sbar head = as and antecedents ends with "?"
    :param dat: data dictionary
    :return: new data dictionary
    '''
    data_dict_new = []
    for sample in dat:
        if sample['anaphor_head'] == 'direct':
            continue

        if sample['anaphor_derived_from'] == 'whether' and 'or' in word_tokenize(sample['artificial_antecedent']):
            continue

        if 'to' == word_tokenize(sample['artificial_antecedent'])[0]:
            continue

        filter_tokens = ["0", "*", "*T*", "*U*", "*?*", "*PRO*", "*RNR*", "*ICH*", "*EXP*", "*NOT*", "-LRB-", "-RRB-"]
        org_sent_split = word_tokenize(sample['original_sentence'])
        org_sent_clean = [w for w in org_sent_split if w not in filter_tokens]

        if sample['anaphor_derived_from'].split('-')[0] == 'as' and '?' in org_sent_clean[-2:]:
            continue
        data_dict_new.append(sample)
    return data_dict_new


def pos_part_negative(pos, neg):
    '''
    :param pos: antecedent string
    :param neg: negative candidate string
    :return: True if negative candidate contains positive candidate, else False
    '''
    if pos == neg:
        return True
    pos_token = word_tokenize(pos)
    neg_token = word_tokenize(neg)

    if len(neg_token) < len(pos_token):
        return False
    else:
        for i in range(len(neg_token)-len(pos_token)):
            if neg_token[i:i+len(pos_token)] == pos_token:
                return True
        return False


def extend_positives_list(plist, ptag_list, nlist, ntag_list, anaphor_derived_from, anaph_sent):
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
    indices = []

    anaph_sent_token = word_tokenize(anaph_sent.replace("-X-", " -X- "))
    prev_word = anaph_sent_token[anaph_sent_token.index('-X-') - 1]

    for i, (negative, negative_tag) in enumerate(zip(nlist, ntag_list)):
        for positive, positive_tag in zip(plist_temp, plist_tag_temp):
            if pos_part_negative(positive, negative):
                indices.append(i)
                continue

            if word_tokenize(negative)[0] == anaphor_derived_from.split('-')[0]:
                indices.append(i)
                continue

            if word_tokenize(negative)[0] == prev_word:
                indices.append(i)
                continue

            sym_difference = list(set(word_tokenize(positive)) ^ set(word_tokenize(negative)))
            punctuation = list(string.punctuation)
            intersection_strip = [s for s in sym_difference if s not in punctuation]

            if len(intersection_strip) <= 1:
                indices.append(i)
                plist.append(negative)
                ptag_list.append(negative_tag)

    nlist_final = [x for i, x in enumerate(nlist) if i not in indices]
    ntag_list_final = [x for i, x in enumerate(ntag_list) if i not in indices]

    return plist, ptag_list, nlist_final, ntag_list_final


def move2antecedent(data_dic):
    '''
    Extend the list of antecedents with negative candidates that differ in one word and any number of punctuation symbols.
    :param data_dic: data dictionary
    :return: new data dictionary
    '''
    data_dict_new = []
    for i, sample in enumerate(data_dic):
        if i % 1000 == 0:
            print 'example ' + str(i+1)

        plist_ext, ptag_list_ext, nlist_ext, ntag_list_ext = extend_positives_list([sample['artificial_antecedent']],
                                                                                   ['S'],
                                                                                   sample['candidates_0'],
                                                                                   sample['candidates_nodes_0'],
                                                                                   sample['anaphor_derived_from'],
                                                                                   sample['artificial_source_suggestion'])

        sample['antecedents'] = plist_ext
        sample['antecedent_nodes'] = ptag_list_ext

        sample['candidates'] = nlist_ext
        sample['candidates_nodes'] = ntag_list_ext
        sample['candidates_0'] = nlist_ext
        sample['candidates_nodes_0'] = ntag_list_ext
        del sample['later_context']
        data_dict_new.append(sample)
    return data_dict_new


def clean(dat):
    '''
    Remove noise from data.
    :param dat: data dictionary
    :return: new data dictionary
    '''
    data_dict_new = []
    for sample in dat:
        # remove noisy tokens
        filter_tokens = ["0", "*", "*T*", "*U*", "*?*", "*PRO*", "*RNR*", "*ICH*", "*EXP*", "*NOT*", "-LRB-", "-RRB-"]

        asplit = word_tokenize(sample['artificial_antecedent'])
        atemp = []
        for w in asplit:
            if w not in filter_tokens:
                atemp.append(w)
        antecedent_clean = ' '.join(atemp)
        sample['artificial_antecedent'] = antecedent_clean

        asent_split = word_tokenize(sample['artificial_source_suggestion'])
        asent_temp = []
        for w in asent_split:
            if w not in filter_tokens:
                asent_temp.append(w)
        asent_clean = ' '.join(asent_temp)
        sample['artificial_source_suggestion'] = asent_clean

        for k in range(5):
            canidates_clean = []
            for c in sample['candidates_'+str(k)]:
                csplit = word_tokenize(c)
                ctemp = []
                for w in csplit:
                    if w not in filter_tokens:
                        ctemp.append(w)
                canidates_clean.append(' '.join(ctemp))
            sample['candidates_'+str(k)] = canidates_clean
        data_dict_new.append(sample)
    return data_dict_new


def remove_duplicate_candidates(dat):
    '''
    Remove duplicates from the negative candidate list, by taking the one which occurs first.
    :param dat: data dictionary
    :return: new data dictionary
    '''
    data_dict_new = []
    for sample in dat:
        for k in range(5):
            neg_candidates = sample['candidates_'+str(k)]
            neg_candidate_tags = sample['candidates_nodes_'+str(k)]

            neg_candidates_filter = []
            neg_candidate_filter_tags = []
            for n, t in zip(neg_candidates, neg_candidate_tags):
                if n not in neg_candidates_filter:
                    neg_candidates_filter.append(n)
                    neg_candidate_filter_tags.append(t)
            assert len(list(set(neg_candidates_filter))) == len(list(neg_candidates_filter))

            sample['candidates_'+str(k)] = neg_candidates_filter
            sample['candidates_nodes_'+str(k)] = neg_candidate_filter_tags
        data_dict_new.append(sample)
    return data_dict_new


def check_antecedents(dat):
    '''
    Assert that the antecedent list has no duplicates.
    :param dat: data dictionary
    '''
    for sample in dat:
        antecedents = sample['antecedents']
        assert len(list(set(antecedents))) == len(list(antecedents))

        for i, a1 in enumerate(antecedents):
            for j, a2 in enumerate(antecedents):
                if i != j:
                    assert abs(len(a1) - len(a2)) <= 1


if __name__ == '__main__':
    with open(sys.argv[1] + '_out', "r") as f:
        dat1 = json.load(f)

    dat2 = filter_by_antecedent_and_anaphor(dat1)
    dat3 = clean(dat2)
    dat4 = remove_duplicate_candidates(dat3)
    dat5 = move2antecedent(dat4)

    with open(sys.argv[1] + '.json', 'w') as f:
        f.write(json.dumps(dat5, indent=4, sort_keys=True))

