package pa.general;

import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * Removes duplicate candidates from a given JSON file.
 * This basically does the same as the method @pa.csn.CSNPostProcessing.removeDuplicates() but is not bound
 * to the context of the other alterations needed specific to the CSN data set and was thus used
 * for the ASN and artifical data sets.
 */
public class RemoveDuplicates {

	public static class Tuple<T, U> {
		public final T _1;
		public final U _2;
		
		public Tuple(T arg1, U arg2) {
			super();
		    this._1 = arg1;
		    this._2 = arg2;
		}
	}
	
	public static Tuple<JSONArray, JSONArray> removeDuplicates( JSONArray candidates, JSONArray candidate_tags ){
		List<String> candidates_old = (ArrayList) candidates;
		List<String> candidateConstituencyTags = (ArrayList) candidate_tags;
		
		List<String> finalCandidates = new ArrayList<String>();
	    List<String> finalCandidateConstituencyTags = new ArrayList<String>();
	    Map<String, Map<String, Integer>> map = new HashMap<String, Map<String, Integer>>();	// {duplicate-candidate -> {tag1->#occ, tag2->#occ}}
		Set<String> set1 = new HashSet<String>();
		for( int i = 0; i < candidates_old.size(); i++ ){
			String cand = candidates_old.get(i);
			if( !set1.add(cand) ){
				String tag = candidateConstituencyTags.get(i);
				if( !map.containsKey(cand) ){
					int indexOfFirstCandOcc = candidates_old.indexOf(cand);
					String firstTag = candidateConstituencyTags.get(indexOfFirstCandOcc);
					map.put(cand, new HashMap<String, Integer>());
					map.get(cand).put(firstTag, 1);
				}
				Map<String, Integer> tagOccurences = map.get(cand);
				if( tagOccurences.containsKey(tag) ){
					int oldOccurence = tagOccurences.get(tag);
					tagOccurences.put(tag, oldOccurence + 1);
				}
				else{
					tagOccurences.put(tag, 1);
				}
			}
		}
		if( !map.isEmpty() ){
			for( String key : map.keySet() ){
				Map<String, Integer> tagToOcc = map.get(key);
				
				Map.Entry<String, Integer> maxEntry = null;
				for( Map.Entry<String, Integer> entry : tagToOcc.entrySet() ){
					if( maxEntry == null || entry.getValue().compareTo(maxEntry.getValue()) > 0 ||
							( entry.getValue().compareTo(maxEntry.getValue()) == 0 ) && entry.getKey().equals("VP") ){	// this makes VP more important than other tags with same frequency
						maxEntry = entry;
					}
				}
				finalCandidates.add(key);
				finalCandidateConstituencyTags.add(maxEntry.getKey());
			}
		}
		
		for( int t = 0; t < candidates_old.size(); t++ ){
			String aCandidate = candidates_old.get(t);
			if( !finalCandidates.contains(aCandidate) ){
				finalCandidates.add(aCandidate);
				finalCandidateConstituencyTags.add(candidateConstituencyTags.get(t));
			}
		}
		
		JSONArray final_candidates = new JSONArray();
		JSONArray final_candidate_tags = new JSONArray();
		for( String cand : finalCandidates ){
			final_candidates.add(cand);
		}
		for( String tag : finalCandidateConstituencyTags ){
			final_candidate_tags.add(tag);
		}
		
		Tuple<JSONArray, JSONArray> result = new Tuple<JSONArray, JSONArray>(final_candidates, final_candidate_tags);
		return result;
	}
	
	/**
	 * @param path_to_original_file
	 * @param path_to_new_file
	 */
	public static void removeDuplicates( String path_to_original_file, String path_to_new_file ){
		try{
			BufferedWriter writer = new BufferedWriter(new FileWriter(path_to_new_file) );
			writer.write("[");
	        writer.newLine();
	        
	        JSONParser parser = new JSONParser();
			try{
				System.out.println("Parsing file...");
				JSONArray full_csn = (JSONArray) parser.parse(new FileReader(path_to_original_file));
				System.out.println("Done!");
				for( int i = 0; i < full_csn.size(); i++ ){
					Object o = full_csn.get(i);
					JSONObject testsetentry = (JSONObject) o;
					JSONObject obj = testsetentry;	
					
					JSONArray posCand = (JSONArray) testsetentry.get("antecedent");
					JSONArray posCandTags = (JSONArray) testsetentry.get("antecedent_all_node");
					Tuple<JSONArray, JSONArray> noPosDuplicates = removeDuplicates(posCand, posCandTags);
					obj.put("antecedent", noPosDuplicates._1);
					obj.put("antecedent_all_node", noPosDuplicates._2);
					
					JSONArray posBestCand = (JSONArray) testsetentry.get("antecedent_best");
					JSONArray posBestCandTags = (JSONArray) testsetentry.get("antecedent_best_node");
					Tuple<JSONArray, JSONArray> noPosBestDuplicates = removeDuplicates(posBestCand, posBestCandTags);
					obj.put("antecedent_best", noPosBestDuplicates._1);
					obj.put("antecedent_best_node", noPosBestDuplicates._2);
					
					for( Object o2 : testsetentry.keySet() ){
						String keyname = (String) o2;
						if( keyname.startsWith("candidates_nodes") ){
							String candKeyName = keyname.replace("_nodes", "");
							JSONArray tmpCand = (JSONArray) testsetentry.get(candKeyName);
							JSONArray tmpCandTags = (JSONArray) testsetentry.get(keyname);
							Tuple<JSONArray, JSONArray> noDuplicates = removeDuplicates(tmpCand, tmpCandTags);
							obj.put(candKeyName, noDuplicates._1);
							obj.put(keyname, noDuplicates._2);
						}
					}

				    writer.write(obj.toJSONString());
				    if( i != full_csn.size() - 1 ){
				    	writer.write(",");
				    }
				    writer.newLine();
				    writer.flush();
				}
			}
			catch( Exception e ){
				e.printStackTrace();
			}
			writer.write("]");
			writer.flush();
			writer.close();
		}
		catch( Exception e ){
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		if( args.length == 2 ){
			removeDuplicates(args[0], args[1]);
		}
		else{
			System.err.print("Need two arguments, first a path to the original file, second a path to the new file to be created.");
		}
	}

}
