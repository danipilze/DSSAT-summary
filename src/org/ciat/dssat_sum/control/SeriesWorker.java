package org.ciat.dssat_sum.control;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import org.ciat.dssat_sum.model.VariableLocation;
import org.ciat.dssat_sum.model.Sampling;
import org.ciat.dssat_sum.model.CropCode;
import org.ciat.dssat_sum.model.ProgressBar;
import org.ciat.dssat_sum.model.SummaryRun;
import org.ciat.dssat_sum.model.Treatment;
import org.ciat.dssat_sum.model.Utils;
import org.ciat.dssat_sum.model.Variable;

public class SeriesWorker {

	private Set<VariableLocation> locations;
	private String[] inputCoeficientsNames;
	private Map<Integer, String> inputCoeficients; // pair of run a coeficients

	private SummaryRun run;

	public SeriesWorker(SummaryRun summaryRun) {
		this.run = summaryRun;
	}

	public void work() {

		locations = getVariables(run.getModel());
		Map<Integer, Treatment> samplings = getSampleMeasurements();
		Map<Integer, Treatment> simulations = new LinkedHashMap<Integer, Treatment>();
		inputCoeficients = new LinkedHashMap<>();
		ProgressBar bar = new ProgressBar();
		int subFolderIndex = 0;
		DecimalFormat df = new DecimalFormat("00");
		String id_ = "";
		double measured, simulated;

		File CSV = run.getSummaryCSVOutput();
		File JSON = run.getSummaryJSONOutput();

		boolean c = App.prop.getProperty("output.summary.csv").contains("Y"); // check if user wants output in CSV
		boolean j = App.prop.getProperty("output.summary.json").contains("Y"); // check if user wants output in JSON

		try (BufferedWriter CSVWriter = new BufferedWriter(new PrintWriter(CSV)); BufferedWriter JSONWriter = new BufferedWriter(new PrintWriter(JSON));) {

			populateInputCoeficients();

			/* Building the header */
			String head = SummaryRun.CANDIDATE_LABEL + SummaryRun.LINE_SEPARATOR + SummaryRun.DATE_LABEL + SummaryRun.LINE_SEPARATOR + SummaryRun.TREATMENT_LABEL + SummaryRun.LINE_SEPARATOR;

			for (VariableLocation var : locations) {
				head += SummaryRun.MEASURED_PREFIX + var.getVariable().getName() + SummaryRun.LINE_SEPARATOR;
				head += SummaryRun.SIMULATED_PREFIX + var.getVariable().getName() + SummaryRun.LINE_SEPARATOR;
			}

			if (c) {
				CSVWriter.write(head);
				CSVWriter.newLine();
			}
			/* END building the header **/

			/* Search on each run the PlanGro.OUT file from 0/ folder and further */
			boolean flagFolder = true;
			for (int folder = 0; flagFolder; folder++) {
				File bigFolder = new File(folder + SummaryRun.PATH_SEPARATOR);
				subFolderIndex = 0;
				if (bigFolder.exists()) {
					bar = new ProgressBar();
					System.out.println("Getting summary on folder " + bigFolder.getName());

					int subFoderTotal = bigFolder.listFiles().length;

					for (File subFolder : bigFolder.listFiles()) { // for each subfolder
						// look at the overview.out file
						File output = new File(subFolder.getAbsolutePath() + SummaryRun.PATH_SEPARATOR + "PlantGro.OUT");
						if (output.exists()) {
							simulations = getSimulatedMeasurements(output); // get simulated values for that run

							// print full file
							for (Integer tIndex : samplings.keySet()) {
								int sampleNumber = 0;

								for (String date : samplings.get(tIndex).getSamplings().keySet()) {
									sampleNumber++;
									
									//check if the treatment was simulated
									if (simulations.get(tIndex.intValue()) != null) { 
										// check if that date was simulated
										if (simulations.get(tIndex.intValue()).getSamplings().get(date) != null) {
											
											if (c) {
												CSVWriter.write(subFolder.getName() + SummaryRun.LINE_SEPARATOR);
												CSVWriter.write(date + SummaryRun.LINE_SEPARATOR);
												CSVWriter.write(tIndex.intValue() + SummaryRun.LINE_SEPARATOR);
											}

											/* printing in JSON */
											id_ = df.format(tIndex.intValue()) + df.format(sampleNumber) + subFolder.getName();
											if (j) {
												JSONWriter.write("{\"index\":{\"_index\":\"summary\",\"_type\":\"sampling\",\"_id\":" + Long.parseLong(id_) + "}}");
												JSONWriter.newLine();
												JSONWriter.write("{");
												JSONWriter.write("\"" + SummaryRun.KIBANA_INDEX + SummaryRun.CANDIDATE_LABEL + "\":" + Integer.parseInt(subFolder.getName()) + ",");
												JSONWriter.write("\"" + SummaryRun.KIBANA_INDEX + SummaryRun.DATE_LABEL + "\":\"" + date + "\",");
												JSONWriter.write("\"" + SummaryRun.KIBANA_INDEX + SummaryRun.TREATMENT_LABEL + "\":" + tIndex.intValue() + ",");
											}
											for (Variable var : samplings.get(tIndex).getSamplings().get(date).getValues().keySet()) {
												measured = samplings.get(tIndex.intValue()).getSamplings().get(date).getValues().get(var).doubleValue();
												simulated = simulations.get(tIndex.intValue()).getSamplings().get(date).getValues().get(var).doubleValue();
												/* printing in CSV */
												if (c) {
													CSVWriter.write(measured + SummaryRun.LINE_SEPARATOR);
													CSVWriter.write(simulated + SummaryRun.LINE_SEPARATOR);
												}

												/* printing in JSON */
												if (j) {
													JSONWriter.write("\"" + SummaryRun.KIBANA_INDEX + SummaryRun.MEASURED_PREFIX + var.getName() + "\":" + measured + ",");
													JSONWriter.write("\"" + SummaryRun.KIBANA_INDEX + SummaryRun.SIMULATED_PREFIX + var.getName() + "\":" + simulated + ",");
												}
											}
											String[] values = inputCoeficients.get(Integer.parseInt(subFolder.getName())).split(" ");
											for (int i = 0; i < values.length; i++) {
												if (j) {
													JSONWriter.write("\"" + SummaryRun.KIBANA_INDEX + SummaryRun.COEFFICIENT_PREFIX + inputCoeficientsNames[i] + "\":" + values[i] + ",");
												}
											}
											if (c) {
												CSVWriter.newLine();
											}
											if (j) {
												JSONWriter.write("\"" + SummaryRun.KIBANA_INDEX + "id" + "\":\"" + id_ + "\"");
												JSONWriter.write("}");
												JSONWriter.newLine();
											}
										}
									}

								}

							}

						} else {
							App.log.warning(subFolder + SummaryRun.PATH_SEPARATOR + output.getName() + " not found");
						}

						subFolderIndex++;
						if (subFolderIndex % 100 == 0) {
							bar.update(subFolderIndex, subFoderTotal);
						}

					}
					bar.update(subFoderTotal - 1, subFoderTotal);

				} else {
					flagFolder = false; // Flag that there are no more folders search in
					App.log.fine("Finished gathering simulated results");
				}
			}

			// pwriter.close();
			// bwriter.close();
		} catch (FileNotFoundException e) {
			App.log.severe("File not found " + CSV.getAbsolutePath());
		} catch (IOException e) {
			App.log.severe("Error writing in " + CSV.getAbsolutePath());
		}

	}

	private void populateInputCoeficients() {

		try (BufferedReader inHead = new BufferedReader(new InputStreamReader(new FileInputStream(App.prop.getProperty("crop.name") + ".CUL")))) {
			String line = "";
			int indexVars = 0;
			while ((line = inHead.readLine()) != null) {
				// if header populate variables names
				if (line.contains("ECO#")) {

					indexVars = line.replaceAll("ECO#", "ECO;").indexOf(";") + 1;

					line = line.split("#")[2];
					/* Leave the line with only one space of separation */
					while (line.contains("  ")) {
						line = line.replaceAll("  ", " ");
					}
					line = line.trim();

					inputCoeficientsNames = line.split(" "); // divide in spaces
				} else {
					String first = line.split(" ")[0];

					if (Utils.isNumeric(first)) {
						line = line.substring(indexVars);
						line = line.replaceAll("#", "");
						/* Leave the line with only one space of separation */
						while (line.contains("  ")) {
							line = line.replaceAll("  ", " ");
						}
						line = line.trim();
						inputCoeficients.put(Integer.parseInt(first), line);

					}
				}

			}
		} catch (IOException e) {
			App.log.severe("File not found " + App.prop.getProperty("crop.name") + ".CUL");
		}
	}

	private Map<Integer, Treatment> getSimulatedMeasurements(File plantGro) {
		Map<Integer, Treatment> treatments = new LinkedHashMap<>();

		String line = "";
		String[] numbers;
		Treatment t = new Treatment(-1);
		Sampling m = new Sampling();
		int doy = 0;
		int year = 0;
		Calendar calendar = Calendar.getInstance();

		if (plantGro.exists()) {
			try (Scanner reader = new Scanner(plantGro)) {
				while (reader.hasNextLine()) {
					line = reader.nextLine();
					line = line.trim();

					/* Leave the line with only one space of separation */
					while (line.contains("  ")) {
						line = line.replaceAll("  ", " ");
					}

					numbers = line.split(" "); // divide in spaces

					if (line.contains("TREATMENT")) { // detecting the treatment
						if (t != new Treatment(Integer.parseInt(numbers[1]))) {
							t = new Treatment(Integer.parseInt(numbers[1]));
						}
					}

					// In values section
					if (numbers.length > 0 && Utils.isNumeric(numbers[0])) {

						year = Integer.parseInt(numbers[0]); // obtain the year
						doy = Integer.parseInt(numbers[1]); // obtain the DOY

						calendar.set(Calendar.DAY_OF_YEAR, doy);
						calendar.set(Calendar.YEAR, year);
						calendar.set(Calendar.HOUR, 0);
						calendar.set(Calendar.MINUTE, 0);
						calendar.set(Calendar.SECOND, 0);

						for (VariableLocation vl : locations) {
							m.getValues().put(vl.getVariable(), Double.parseDouble(numbers[vl.getIndexPlantGro()]));
						}
						t.getSamplings().put(SummaryRun.DATE_FORMAT.format(calendar.getTime()), m);
						treatments.put(t.getNumber(), t);
					}

				}

				// reader.close();

			} catch (FileNotFoundException e) {
				App.log.severe("File not found as " + plantGro.getAbsolutePath());
			}
		} else {
			App.log.warning("File not found " + plantGro.getAbsolutePath());
		}

		return treatments;

	}

	private Map<Integer, Treatment> getSampleMeasurements() {
		Map<Integer, Treatment> treatments = new LinkedHashMap<>();
		File fileT = new File(App.prop.getProperty("fileT.location"));
		Scanner reader;
		String line = "";
		String[] numbers;
		Treatment treatment = new Treatment(-1);
		Treatment newTreatment = new Treatment(-1);
		Sampling meas = new Sampling();
		int doy = 0;
		int year = 0;
		Calendar calendar = Calendar.getInstance();

		try {
			if (fileT.exists()) {
				reader = new Scanner(fileT);
				while (reader.hasNextLine()) {
					line = reader.nextLine();
					line = line.trim();

					/* Leave the line with only one space of separation */
					while (line.contains("  ")) {
						line = line.replaceAll("  ", " ");
					}

					numbers = line.split(" ");
					if (numbers.length > 0 && Utils.isNumeric(numbers[0])) {
						newTreatment = new Treatment(Integer.parseInt(numbers[0]));
						if (!treatment.equals(newTreatment)) {
							treatment = newTreatment;
						}
						// obtain two digits of the year
						year = Integer.parseInt(numbers[1].charAt(0) + "" + numbers[1].charAt(1));

						if (year > 50) {
							year += 1900; // 50 and higher will be 1950
						} else {
							year += 2000; // 49 and lower will be 2049
						}
						// obtain three digits of the DOY
						doy = Integer.parseInt(numbers[1].charAt(2) + "" + numbers[1].charAt(3) + "" + numbers[1].charAt(4));

						calendar.set(Calendar.DAY_OF_YEAR, doy);
						calendar.set(Calendar.YEAR, year);
						calendar.set(Calendar.HOUR, 0);
						calendar.set(Calendar.MINUTE, 0);
						calendar.set(Calendar.SECOND, 0);

						meas = new Sampling();
						// fill the values on that row for all the variables
						for (VariableLocation vl : locations) {
							// add the measurement of each variable
							meas.getValues().put(vl.getVariable(), Double.parseDouble(numbers[vl.getIndexFileT()]));
						}
						// add the sampling with all the values for that day in the treatment
						treatment.getSamplings().put(SummaryRun.DATE_FORMAT.format(calendar.getTime()), meas);
						treatments.put(treatment.getNumber(), treatment);

					}

				}

				reader.close();
			} else {
				App.log.severe("File T not found as " + fileT.getAbsolutePath());
			}

		} catch (FileNotFoundException e) {
			App.log.severe("File T not found as " + fileT.getAbsolutePath());
		}
		return treatments;
	}

	private Set<VariableLocation> getVariables(CropCode modelCode) {
		Set<VariableLocation> vars = new LinkedHashSet<VariableLocation>();

		switch (run.getModel()) {
		case BEAN: {
			// TODO

		}

			break;
		case MAIZE: {
			vars.add(new VariableLocation(new Variable("LAID"), 2, 6));
			vars.add(new VariableLocation(new Variable("CWAD"), 3, 12));
			vars.add(new VariableLocation(new Variable("LWAD"), 4, 7));
			vars.add(new VariableLocation(new Variable("GWAD"), 5, 9));
			vars.add(new VariableLocation(new Variable("HAID"), 6, 15));
		}
			break;
		default: {
			App.log.warning("Crop not configurated for plantgro: " + run.getModel());
		}

		}

		return vars;
	}

}
