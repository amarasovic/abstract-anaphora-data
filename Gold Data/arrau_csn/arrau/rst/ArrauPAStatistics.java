package pa.arrau.rst;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.simple.*;
import org.json.simple.parser.*;

/**
 * This class gives statistics on a given ARRAU PA file of the average number of candidates
 * for d <= {0,1,2,3}.
 */
public class ArrauPAStatistics {

	/**
	 * Takes one argument, the path to the file to be analyzed.
	 *
	 * @param args
	 */
	public static void main( String[] args ){
		JSONParser parser = new JSONParser();
		try{
			JSONArray a = (JSONArray) parser.parse(new FileReader(args[0]));
			
			List<Integer> candidateD0List = new ArrayList<Integer>(), candidateD1List = new ArrayList<Integer>(),
					candidateD2List = new ArrayList<Integer>(), candidateD3List = new ArrayList<Integer>();
			
			for( Object o : a ){
				JSONObject testsetentry = (JSONObject) o;
				JSONArray cand_BR = (JSONArray) testsetentry.get("candidates_minus_all_antecedents");	// broad region
				JSONArray cand_PA = (JSONArray) testsetentry.get("candidates_0_minus_all_antecedents");	// sentence with PA
				
				Set<String> candidates_D0 = new HashSet<String>(), candidates_D1 = new HashSet<String>(),
						candidates_D2 = new HashSet<String>(), candidates_D3 = new HashSet<String>();
				
				for( Object o2 : cand_BR ){
					String cand = (String) o2;
					candidates_D0.add(cand);
					candidates_D1.add(cand);
					candidates_D2.add(cand);
					candidates_D3.add(cand);
				}
				for( Object o2 : cand_PA ){
					String cand = (String) o2;
					candidates_D0.add(cand);
					candidates_D1.add(cand);
					candidates_D2.add(cand);
					candidates_D3.add(cand);
				}
				candidateD0List.add(candidates_D0.size());
				
				if( testsetentry.containsKey("candidates_1_minus_all_antecedents") ){
					JSONArray cand_prec1 = (JSONArray) testsetentry.get("candidates_1_minus_all_antecedents");
					for( Object o2 : cand_prec1 ){
						String cand = (String) o2;
						candidates_D1.add(cand);
						candidates_D2.add(cand);
						candidates_D3.add(cand);
					}
					candidateD1List.add(candidates_D1.size());
				}
				
				if( testsetentry.containsKey("candidates_2_minus_all_antecedents") ){
					JSONArray cand_prec2 = (JSONArray) testsetentry.get("candidates_2_minus_all_antecedents");
					for( Object o2 : cand_prec2 ){
						String cand = (String) o2;
						candidates_D2.add(cand);
						candidates_D3.add(cand);
					}
					candidateD2List.add(candidates_D2.size());				
				}
				
				if( testsetentry.containsKey("candidates_3_minus_all_antecedents") ){
					JSONArray cand_prec3 = (JSONArray) testsetentry.get("candidates_3_minus_all_antecedents");
					for( Object o2 : cand_prec3 ){
						String cand = (String) o2;
						candidates_D3.add(cand);
					}
					candidateD3List.add(candidates_D3.size());
				}
			}
			double d0sum = 0, d1sum = 0, d2sum = 0, d3sum = 0;
			for( int i = 0; i < candidateD0List.size(); i++ ){
				d0sum += candidateD0List.get(i);
			}
			System.out.println("The average number of candidates for d0 is " + d0sum / candidateD0List.size());
			
			for( int i = 0; i < candidateD1List.size(); i++ ){
				d1sum += candidateD1List.get(i);
			}
			System.out.println("The average number of candidates for d1 is " + d1sum / candidateD1List.size());
			
			for( int i = 0; i < candidateD2List.size(); i++ ){
				d2sum += candidateD2List.get(i);
			}
			System.out.println("The average number of candidates for d2 is " + d2sum / candidateD2List.size());
			
			for( int i = 0; i < candidateD3List.size(); i++ ){
				d3sum += candidateD3List.get(i);
			}
			System.out.println("The average number of candidates for d3 is " + d3sum / candidateD3List.size());
		}
		catch( Exception e ){
			e.printStackTrace();
		}
	}

}
