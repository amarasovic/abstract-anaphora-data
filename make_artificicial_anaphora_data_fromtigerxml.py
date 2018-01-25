# -*- coding: utf-8 -*-
"""
Created on Mon Nov 14 12:03:50 2016

@author: opitz
"""
from lxml import etree
import xml.etree.ElementTree as ET
import itertools
from collections import Counter
import numpy as np
import json
from copy import deepcopy
from random import choice
import sys

LANG = "en"

corpus = sys.argv[1]
corpus_full = 'tigersearch_matches/' + corpus + '_full'
corpus_general = 'tigersearch_matches/' + corpus + '_general'
corpus_wh = 'tigersearch_matches/' + corpus + '_wh'
corpus_that = 'tigersearch_matches/' + corpus + '_that'
corpus_relative = 'tigersearch_matches/' + corpus + '_relative'
corpus_since_tmp = 'tigersearch_matches/' + corpus + '_since_tmp'
corpus_since_prp = 'tigersearch_matches/' + corpus + '_since_prp'
corpus_since_vp = 'tigersearch_matches/' + corpus + '_since_vp'
corpus_as_tmp = 'tigersearch_matches/' + corpus + '_as_tmp'
corpus_as_prp = 'tigersearch_matches/' + corpus + '_as_prp'
corpus_as_vp = 'tigersearch_matches/' + corpus + '_as_vp'
corpus_if_adv = 'tigersearch_matches/' + corpus + '_if_adv'
corpus_if_vp = 'tigersearch_matches/' + corpus + '_if_vp'
corpus_output = corpus + '_out'

replacable_sbarheads = {"en": {
                               "direct": [("this", 0), ("that", 0), ("it", 0)],
                               "that": [("that", 0), ("this", 0), ("it", 0)],
                               "this": [("that", 0), ("this", 0), ("it", 0)], # check does this ever occur in Gigaword -> if not remove from the paper
                               "0": [("that", 0), ("this", 0), ("it", 0)],
                               "because": [#("therefore", 0),
                                            ("because of that", 2), ("because of this", 2), ("because of it", 2),
                                            ("due to this", 2), ("due to that", 2), ("due to it", 2)],
                               "if-adv": [("because of that", 2), ("because of this", 2), ("because of it", 2),
                                          ("due to this", 2), ("due to that", 2), ("due to it", 2)],
                               "since-tmp": [('since then', 1)],
                               "since-prp": [("because of that", 2), ("because of this", 2), ("because of it", 2),
                                             ("due to this", 2), ("due to that", 2), ("due to it", 2)],
                               "as-tmp": [("during this", 1), ("during that", 1)],
                               "as-prp": [("because of that", 2), ("because of this", 2), ("because of it", 2),
                                          ("due to this", 2), ("due to that", 2), ("due to it", 2)],
                               "whether": [("this", 0), ("that", 0), ("it", 0)],
                               "after": [("after this", 1), ("after that", 1), ("after it", 1)]
                        }}


# replaces sbar head with possible anaphora and highlights it
def mark_anaphora(sbarhead):
    repl = choice(replacable_sbarheads[LANG][sbarhead]) # e.g. repl = ("after this", 1)
    spl = repl[0].split(" ") # spl = ["after", "this"]
    spl[repl[1]] = "-X-"+spl[repl[1]]+"-X-" # spl[1] = "-X-this-X-"
    return " ".join(spl), repl[0], spl[repl[1]] # "after -X-this-X-", "after this", -X-this-X-


# makes a source suggestions by applying mark_anaphora
def source_suggestion(source, head, context_additional, sbar_additional, sid):
    # e.g. source = ['And', 'even', 'without', 'deals', ',', 'Mr.', 'Connolly', 'figures', '-X-', '.']
    if sid in context_additional and head.split('-')[0] in ['since', 'as', 'if']:
        head = sbar_additional[context_additional.index(sid)]

    idx = source.index("-X-")
    news = deepcopy(source)
    if head in replacable_sbarheads[LANG]:
        news[idx], aphrase, ahead = mark_anaphora(head)
    else:
        news[idx] = "-X--X-"
        aphrase = 'none'
        ahead = 'none'

    return news, news[idx].split("-X-")[1], aphrase, ahead


def readf(p):
    with open(p) as f:
        return f.read()


def parse_data(p=corpus_full):
    return ET.parse(p)


# get all nonterminals as children from given non-terminals, recursive
# usual input: one nt-id, e.g. ["s1_501"]
# EXAMPLE:
# sentence "Hans(s1_1) hits(s1_2) Joe(s1_3)". Nts are S(s1_501) -> NP(s1_502) (VP(s1_503) -> NP(s1_504))
# function call with ["s1_501"] yields all ntids
# call with ["s1_503"] yields ["s1_503","s1_504"]
def get_nonterminalids(ntids, xmlsentence, secedges, secedgesdone=[]):
    if ntids== []:
        return []
    new_nts = []
    terminals = []
    for ntid in ntids:
        # iter sentence
        for nt in xmlsentence.iter("nt"):
            # if father found, search for direct children
            if nt.attrib["id"] == ntid:
                for edge in nt.iter("edge"):
                    idx = edge.attrib["idref"]
                    if int(idx.split("_")[1]) >= 500:
                        new_nts.append(idx)
                    elif idx in secedges:
                        se = secedges[idx]
                        if se not in ntids and se not in secedgesdone:
                            new_nts.append(se)
                            secedgesdone.append(se)
                    """
                    else:
                        mse = maybe_sec_edge(ntid,secedges,xmlsentence)
                        if mse != ntid:
                            ntids.append(mse)
                            print(idx,"->",mse)
                    """
    # recursive call to seek for directs nt-children the direct nt-children
    return ntids + new_nts + get_nonterminalids(new_nts, xmlsentence, secedges, secedgesdone)


# get all terminals as children from given non-terminals, recursive
# usual input: one nt-id, e.g. ["s1_501"]
# EXAMPLE:
# sentence "Hans(s1_1) hits(s1_2) Joe(s1_3)". Nts are S(s1_501) -> NP(s1_502) (VP(s1_503) -> NP(s1_504))
# function call with ["s1_501"] all terminal ids, i.e. (["s1_1",...])
# call with ["s1_503"] yields ["s1_2,"s1_3"], i.e. seeked for VP and points at "hits Joe"
def get_terminalids(ntids, xmlsentence):
    if not ntids:
        return []
    new_nts = []
    terminals = []
    for ntid in ntids:
        for nt in xmlsentence.iter("nt"):
            if nt.attrib["id"] == ntid:
                for edge in nt.iter("edge"):
                    idx=edge.attrib["idref"]
                    if int(idx.split("_")[1]) >= 500:
                        new_nts.append(idx)
                    else:
                        terminals.append(idx)
    return terminals + get_terminalids(new_nts, xmlsentence)


def sent_rep(stree, minlenante=2):
    sen = []
    tempwords = {}
    candidates = []
    candidates_nodes = []
    scandidates = []
    scandidates_nodes = []
    for t in stree.iter("t"):
        sen.append(t.attrib["word"])
        tempwords[t.attrib["id"]] = t.attrib["word"]
    terminal_ids = sorted(tempwords.keys(), key=lambda x: int(x.split("_")[1]))
    terminal_ids = clean(terminal_ids, tempwords)
    for nt in stree.iter("nt"):
        if nt.attrib["cat"] == "S": #and nt.attrib["id"] != sid:
            wids = clean(sorted(get_terminalids([nt.attrib["id"]], stree), key=lambda x: int(x.split("_")[1])), tempwords)
            ws = [tempwords[wid] for wid in wids]
            if len(ws) >= minlenante:
                scandidates.append([ws[0][0].upper() + ws[0][1:]]+ws[1:])
                scandidates_nodes.append(nt.attrib["cat"])
        wids = clean(sorted(get_terminalids([nt.attrib["id"]], stree), key=lambda x: int(x.split("_")[1])), tempwords)
        ws = [tempwords[wid] for wid in wids]
        if len(ws) >= minlenante:
            candidates.append(ws)
            candidates_nodes.append(nt.attrib["cat"])
    return (sen, terminal_ids, tempwords, candidates, candidates_nodes)


# get sentences and 4 previous sentences
def get_p(allsents, idx):
    p = [[], [], [], [], []]
    p[4] = allsents[idx]
    if idx-1 in allsents:
        p[3] = allsents[idx-1]
    if idx-2 in allsents:
        p[2] = allsents[idx-2]
    if idx-3 in allsents:
        p[1] = allsents[idx-3]
    if idx-4 in allsents:
        p[0] = allsents[idx-4]
    return p


# aquire candidates + context
def get_candidates_plus_context(allsents, idx, sentence, sbarid, words, sid, win=3, secedges={}, minlenante=2):
    intidx = int(idx[1:])
    sentsprev = get_p(allsents, intidx)
    prev_context = []
    later_context = []
    candidates01234 = [[], [], [], [], []]
    candidates01234_nodes = [[], [], [], [], []]
    candidates = []
    counter = 0
    for tup in sentsprev[:-1]:
        if tup:
            sen, tids, tmpwords, cans, cans_nodes = tup
            if counter-win < intidx:
                prev_context.append([tmpwords[tid] for tid in tids])
            elif counter+win > intidx:
                later_context.append([tmpwords[tid] for tid in tids])
            candidates01234[counter] = cans
            candidates01234_nodes[counter] = cans_nodes
        counter += 1
    targetsen = sentence
    nontids = list(set(get_nonterminalids([targetsen.attrib["id"]+"_500"], targetsen, secedges)))#sid

    sen = []
    tempwords={}
    for t in targetsen.iter("t"):
        sen.append(t.attrib["word"])
        tempwords[t.attrib["id"]] = t.attrib["word"]
    for ntid in nontids:
        wids = clean(sorted(get_terminalids([ntid],targetsen),key=lambda x: int(x.split("_")[1])),tempwords)
        ws = [tempwords[wid] for wid in wids]
        if len(ws) >= minlenante:
            candidates01234[counter].append(ws)
            for nt in targetsen.iter("nt"):
                if nt.attrib["id"] == ntid:
                    candidates01234_nodes[counter].append(nt.attrib["cat"])
    return candidates, prev_context, later_context, candidates01234, candidates01234_nodes


def make_sample(words, cutoff, head, context_additional, sbar_additional, sid, cleaning=True):
    dic = {}
    terminal_ids = sorted(words.keys(), key=lambda w: int(w.split("_")[1]))
    if cleaning:
        terminal_ids = clean(terminal_ids, words)
    dic["sentence"] = [words[x] for x in terminal_ids]
    dic["term_ids"] = terminal_ids
    dic["true_antecedent_ids"] = [x for x in terminal_ids if x in cutoff]
    dic["true_antecedent"] = [words[x] for x in dic["true_antecedent_ids"]]
    dic["source_ids"] = [x for x in terminal_ids if x not in cutoff and x not in head]
    dic["source"] = [words[x] for x in dic["source_ids"]]

    dic["head_info_ids"] = [h for h in head if h in terminal_ids]
    dic["head_info"] = [words[h] for h in head if h != "direct"]

    if len(dic["head_info"]) == 0:
        dic["head_info"] = ["direct"]

    # when len source = 0 then source = antecedent
    if len(dic["source"]) == 0:
        dic["source_ids"] = terminal_ids
        dic["source"] = dic["sentence"]

    corpus_outputerdic={}
    source = dic["source"]
    if dic["source"][-1] in [";", ",", "\""]:
        source[-1] = "."

    insert = 0
    for i, idx in enumerate(dic["source_ids"]):
        if int(idx.split("_")[1]) < int(dic["true_antecedent_ids"][0].split("_")[1]):
            insert = i
    source = source[:insert+1]+["-X-"]+source[insert+1:]

    corpus_outputerdic["artificial_source"] = source
    corpus_outputerdic["anaphor_head"] = dic["head_info"]
    artificial_source_suggestion, anaph_sugg, aphrase, ahead = source_suggestion(corpus_outputerdic["artificial_source"],
                                                                                 dic["head_info"][0],
                                                                                 context_additional,
                                                                                 sbar_additional,
                                                                                 sid)
    corpus_outputerdic['anaphor_phrase'] = aphrase
    corpus_outputerdic['anaphor_head'] = ahead.replace('-X-', '')

    corpus_outputerdic["artificial_source_suggestion"] = artificial_source_suggestion

    if sid in context_additional and dic["head_info"][0].split('-')[0] in ['since', 'as', 'if']:
        corpus_outputerdic["anaphor_derived_from"] = [sbar_additional[context_additional.index(sid)]]
    else:
        corpus_outputerdic["anaphor_derived_from"] = [dic["head_info"][0]]

    corpus_outputerdic["artificial_antecedent"] = dic["true_antecedent"]
    return corpus_outputerdic


def clean(terminal_ids, words):
    remove_ids = []
    for idx, tid in enumerate(terminal_ids):
        if words[tid] not in ["0", "*", "*T*", "*U*", "*?*", "*PRO*", "*RNR*", "*ICH*", "*EXP*", "*NOT*", "-LRB-",
                              "-RRB-"]:
            remove_ids.append(idx)
    return [terminal_ids[i] for i in remove_ids]


def maybe_sec_edge(subtree, secedges, sentence):
    for nt in sentence.iter("nt"):
        if nt.attrib["id"] == subtree:
            for ch in nt:
                if ch.attrib["idref"] in secedges:
                    return secedges[ch.attrib["idref"]]
    return subtree


# core function, reads data and applies transform
def make_artificial_data(minlen_antecedent=2, candidate_window=3):
    # parse data
    context_full = [item for _, item in etree.iterparse(corpus_full, tag='s')]
    context_general = [item for _, item in etree.iterparse(corpus_general, tag='s')]
    context_wh = [item for _, item in etree.iterparse(corpus_wh, tag='s')]
    context_that = [item for _, item in etree.iterparse(corpus_that, tag='s')]
    context_relative = [item for _, item in etree.iterparse(corpus_relative, tag='s')]
    context_since_tmp = [item for _, item in etree.iterparse(corpus_since_tmp, tag='s')]
    context_since_prp = [item for _, item in etree.iterparse(corpus_since_prp, tag='s')]
    context_since_vp = [item for _, item in etree.iterparse(corpus_since_vp, tag='s')]
    context_as_tmp = [item for _, item in etree.iterparse(corpus_as_tmp, tag='s')]
    context_as_prp = [item for _, item in etree.iterparse(corpus_as_prp, tag='s')]
    context_as_vp = [item for _, item in etree.iterparse(corpus_as_vp, tag='s')]
    context_if_adv = [item for _, item in etree.iterparse(corpus_if_adv, tag='s')]
    context_if_vp = [item for _, item in etree.iterparse(corpus_if_vp, tag='s')]
    
    context_filter = context_wh + context_that + context_relative + context_since_vp + context_as_vp + context_if_vp
    context_additional = context_since_prp + context_since_tmp + context_as_prp + context_as_tmp + context_if_adv
    sbar_additional = ['since-prp']*len(context_since_prp) + ['since-tmp']*len(context_since_tmp) + \
                      ['as-prp']*len(context_as_prp) + ['as-tmp']*len(context_as_tmp) + ['if-adv']*len(context_if_adv)
    context_additional_att = [s.attrib["id"] for s in context_additional]
    context_clean = [item for item in context_general if item not in context_filter] + context_additional

    allsentences = {}
    samplelist = []

    # xml parse with no memory explosion based on https://www.ibm.com/developerworks/xml/library/x-hiperfparse/
    xco = -1
    for elm in context_full:
        xco += 1
        allsentences[int(elm.attrib["id"][1:])] = sent_rep(elm, minlen_antecedent)
        elm.clear()
        for ancestor in elm.xpath('ancestor-or-self::*'):
            while ancestor.getprevious() is not None:
                del ancestor.getparent()[0]
        if xco % 1000 == 0:
            print(xco, "of all read")

    for sidx, sentence in enumerate(context_clean):
        senid = sentence.attrib["id"]
        words = {}
        sbarids = []
        sids = []
        secedges = {}

        for terminal in sentence.iter("t"):
            word = terminal.attrib["word"]
            idx = terminal.attrib["id"]
            words[idx] = word
            # has sec edge?
            # id, idref, some gold parses have secondary edges
            # e.g. <t id="s134_14" word="*T*" pos="-NONE-"> <secedge label="*T*" idref="s134_510" /></t>
            if len(terminal) > 0:
                # <Element secedge at 0x1201aa0e0>
                idref = terminal[0].attrib["idref"]
                secedges[idx] = idref

        for match in sentence.iter("variable"):
            if match.attrib["name"] == "#sbar":
                sbarid = match.attrib["idref"]
                sbarids.append(sbarid)
            if match.attrib["name"] == "#s":
                sids.append(match.attrib["idref"])
            #if match.attrib["name"] == "#vp":
            #    vpids.append(match.attrib["idref"])

        for matchidx, sbarid in enumerate(sbarids):
            for nt in sentence.iter("nt"):
                if nt.attrib["id"] == sbarid:
                    if len(nt) > 1:
                        head = []
                        for edge in nt:
                            if len(edge.attrib["idref"].split("_")[1]) == 3: # e.g. s131_504
                                subtree = edge.attrib["idref"]
                                subtree = maybe_sec_edge(subtree, secedges, sentence)
                            else:
                                head.append(edge.attrib["idref"])
                    else:
                        head = ["direct"]
                        subtree = nt[0].attrib["idref"]
                        # sid = subtree
                # e.g. head = ['s3433_10']

            cutoff = get_terminalids([subtree], sentence)
            terminal_ids = sorted(words.keys(), key=lambda z: int(z.split("_")[1]))

            # target = [words[x] for x in terminal_ids if x in cutoff]
            # wotarget = [words[x] for x in terminal_ids if x not in cutoff and x not in head]
            cld = clean(terminal_ids, words)
            cldco = clean(cutoff, words)
            if len(cld) - len(cldco) <= len(cld) - minlen_antecedent:
                candidates, pc, lc, candidates01234, candidates01234_nodes = get_candidates_plus_context(allsentences,
                                                                                                         senid,
                                                                                                         sentence,
                                                                                                         sbarid,
                                                                                                         words,
                                                                                                         sids[matchidx],
                                                                                                         candidate_window,
                                                                                                         secedges)
                # get_candidates_plus_context --> I changed secedges = [] to secedges = {}

                sdic = {}
                sdic_temp = make_sample(words, cutoff, head, context_additional_att, sbar_additional, senid)
                for x, y in sdic_temp.items():
                    sdic[x] = y if isinstance(y, basestring) else ' '.join(y)

                sdic["prev_context"] = [" ".join(c) for c in pc]
                sdic["later_context"] = [" ".join(c) for c in lc]
                sdic["original_sentence"] = " ".join([words[k] for k in terminal_ids])
                sdic["candidates_0"] = [" ".join(c) for c in candidates01234[4]]
                sdic["candidates_1"] = [" ".join(c) for c in candidates01234[3]]
                sdic["candidates_2"] = [" ".join(c) for c in candidates01234[2]]
                sdic["candidates_3"] = [" ".join(c) for c in candidates01234[1]]
                sdic["candidates_4"] = [" ".join(c) for c in candidates01234[0]]
                sdic["candidates_nodes_0"] = candidates01234_nodes[4]
                sdic["candidates_nodes_1"] = candidates01234_nodes[3]
                sdic["candidates_nodes_2"] = candidates01234_nodes[2]
                sdic["candidates_nodes_3"] = candidates01234_nodes[1]
                sdic["candidates_nodes_4"] = candidates01234_nodes[0]
                sdic["path_to_doc"] = str(senid)
                samplelist.append(sdic)
        sentence.clear()

        for ancestor in sentence.xpath('ancestor-or-self::*'):
            while ancestor.getprevious() is not None:
                del ancestor.getparent()[0]

        if (sidx+1) % 10000 == 0:
            print(sidx, "sentences done")

    with open(corpus_output, "w") as fs:
        fs.write(json.dumps(samplelist, indent=4, sort_keys=True))


if __name__ == '__main__':
    make_artificial_data()




