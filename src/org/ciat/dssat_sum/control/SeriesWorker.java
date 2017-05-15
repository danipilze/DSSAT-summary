package org.ciat.dssat_sum.control;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.Scanner;
import java.util.Set;
import org.ciat.dssat_sum.model.VariableLocation;
import org.ciat.dssat_sum.model.Measurement;
import org.ciat.dssat_sum.model.ModelCode;
import org.ciat.dssat_sum.model.ProgressBar;
import org.ciat.dssat_sum.model.SummaryRun;
import org.ciat.dssat_sum.model.Treatment;
import org.ciat.dssat_sum.model.Variable;

public class SeriesWorker {

	private static final String MEASURED_PREFIX = "M_";
	private static final String SIMULATED_PREFIX = "S_";
	private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd"); // ISO 8601
	private Set<VariableLocation> variables;

	private SummaryRun run;

	public SeriesWorker(SummaryRun summaryRun) {
		this.run = summaryRun;
	}

	public void work() {

		variables = getVariables(run.getModel());
		Set<Treatment> samplings = getSampleMeasurements();
		Set<Treatment> simulations = new LinkedHashSet<>();
		ProgressBar bar = new ProgressBar();
		int subFolderIndex = 0;
		DecimalFormat df = new DecimalFormat("00");
		String id_="";

		File CSV = run.getSummaryCSVOutput();
		File JSON = run.getSummaryJSONOutput();
		try (BufferedWriter CSVWriter = new BufferedWriter(new PrintWriter(CSV)); BufferedWriter JSONWriter = new BufferedWriter(new PrintWriter(JSON))) {

			/* Building the header */
			String head = SummaryRun.CANDIDATE_LABEL + SummaryRun.LINE_SEPARATOR + SummaryRun.DATE_LABEL + SummaryRun.LINE_SEPARATOR + SummaryRun.TREATMENT_LABEL + SummaryRun.LINE_SEPARATOR;

			for (VariableLocation var : variables) {
				head += MEASURED_PREFIX + var.getVariable().getName() + SummaryRun.LINE_SEPARATOR;
				head += SIMULATED_PREFIX + var.getVariable().getName() + SummaryRun.LINE_SEPARATOR;
			}

			CSVWriter.write(head);
			CSVWriter.newLine();
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
					;
					for (File subFolder : bigFolder.listFiles()) { // for each subfolder
						// look at the overview.out file
						File output = new File(subFolder.getAbsolutePath() + SummaryRun.PATH_SEPARATOR + "PlantGro.OUT");
						if (output.exists()) {
							simulations = getSimulatedMeasurements(output); // get simulated values for that run

							// print full file
							for (Treatment sampleTreatment : samplings) {
								for (Treatment simulationTreatment : simulations) {

									if (sampleTreatment.equals(simulationTreatment)) { // when treatments matches
										int sampleNumber = 0;
										for (Measurement msample : sampleTreatment.getSamplings()) {
											sampleNumber++;
											for (Measurement msimule : simulationTreatment.getSamplings()) {

												if (msample.getDate().equals(msimule.getDate())) { // when dates matches
													CSVWriter.write(subFolder.getName() + SummaryRun.LINE_SEPARATOR);
													CSVWriter.write(msample.getDate() + SummaryRun.LINE_SEPARATOR);
													CSVWriter.write(sampleTreatment.getNumber() + SummaryRun.LINE_SEPARATOR);

													id_ = subFolder.getName() + "" + df.format(sampleTreatment.getNumber()) + "" + df.format(sampleNumber);
													
													JSONWriter.write("{\"index\":{\"_index\":\"summary\",\"_type\":\"sampling\",\"_id\":" + Integer.parseInt(id_) + "}}");
													JSONWriter.newLine();
													JSONWriter.write("{");
													JSONWriter.write("\"" + SummaryRun.CANDIDATE_LABEL + "\":" + Integer.parseInt(subFolder.getName()) + ",");
													JSONWriter.write("\"" + SummaryRun.DATE_LABEL + "\":\"" + msample.getDate() + "\",");
													JSONWriter.write("\"" + SummaryRun.TREATMENT_LABEL + "\":" + sampleTreatment.getNumber() + ",");

													for (Variable var : msimule.getValues().keySet()) {
														CSVWriter.write(msample.getValues().get(var).doubleValue() + SummaryRun.LINE_SEPARATOR);
														CSVWriter.write(msimule.getValues().get(var).doubleValue() + SummaryRun.LINE_SEPARATOR);

														JSONWriter.write("\"" + MEASURED_PREFIX + var.getName() + "\":" + msample.getValues().get(var).doubleValue() + ",");
														JSONWriter.write("\"" + SIMULATED_PREFIX + var.getName() + "\":" + msimule.getValues().get(var).doubleValue() + ",");
													}
													CSVWriter.newLine();

													//JSONWriter.write("\"" + "Origin" + "\":\"" + run.getSummaryJSONOutput() + "\",");
													JSONWriter.write("\"" + "id" + "\":\"" + id_ + "\"");
													JSONWriter.write("}");
													JSONWriter.newLine();

												}
											}
										}

									}
								}
								// bwriter.flush();
							}

						} else {
							App.LOG.warning(subFolder + SummaryRun.PATH_SEPARATOR + output.getName() + " not found");
						}

						subFolderIndex++;
						if (subFolderIndex % 100 == 0) {
							bar.update(subFolderIndex, subFoderTotal);
						}

					}
					bar.update(subFoderTotal - 1, subFoderTotal);

				} else {
					flagFolder = false; // Flag that there are no more folders search in
					App.LOG.fine("Finished gathering simulated results");
				}
			}

			// pwriter.close();
			// bwriter.close();
		} catch (FileNotFoundException e) {
			App.LOG.severe("File not found " + CSV.getAbsolutePath());
		} catch (IOException e) {
			App.LOG.severe("Error writing in " + CSV.getAbsolutePath());
		}

	}

	private Set<Treatment> getSimulatedMeasurements(File plantGro) {
		Set<Treatment> treatments = new LinkedHashSet<>();

		String line = "";
		String[] numbers;
		Treatment t = new Treatment(-1);
		Measurement m = new Measurement("");
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
					if (numbers.length > 0 && isNumeric(numbers[0])) {

						year = Integer.parseInt(numbers[0]); // obtain the year
						doy = Integer.parseInt(numbers[1]); // obtain the DOY

						calendar.set(Calendar.DAY_OF_YEAR, doy);
						calendar.set(Calendar.YEAR, year);
						calendar.set(Calendar.HOUR, 0);
						calendar.set(Calendar.MINUTE, 0);
						calendar.set(Calendar.SECOND, 0);

						m = new Measurement(sdf.format(calendar.getTime()));
						for (VariableLocation vl : variables) {
							m.getValues().put(vl.getVariable(), Double.parseDouble(numbers[vl.getIndexPlantGro()]));
						}
						t.getSamplings().add(m);
						treatments.add(t);
					}

				}

				// reader.close();

			} catch (FileNotFoundException e) {
				App.LOG.severe("File not found as " + plantGro.getAbsolutePath());
			}
		} else {
			App.LOG.warning("File not found " + plantGro.getAbsolutePath());
		}

		return treatments;

	}

	private Set<Treatment> getSampleMeasurements() {
		Set<Treatment> treatments = new LinkedHashSet<>();
		File fileT = new File(run.getFileT());
		Scanner reader;
		String line = "";
		String[] numbers;
		Treatment treatment = new Treatment(-1);
		Treatment newTreatment = new Treatment(-1);
		Measurement m = new Measurement("");
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
					if (numbers.length > 0 && isNumeric(numbers[0])) {
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

						m = new Measurement(sdf.format(calendar.getTime()));
						for (VariableLocation vl : variables) {
							m.getValues().put(vl.getVariable(), Double.parseDouble(numbers[vl.getIndexFileT()]));
						}
						treatment.getSamplings().add(m);
						treatments.add(treatment);

					}

				}

				reader.close();
			} else {
				App.LOG.severe("File T not found as " + fileT.getAbsolutePath());
			}

		} catch (FileNotFoundException e) {
			App.LOG.severe("File T not found as " + fileT.getAbsolutePath());
		}
		return treatments;
	}

	private Set<VariableLocation> getVariables(ModelCode modelCode) {
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
			App.LOG.warning("Crop not configurated for plantgro: " + run.getModel());
		}

		}

		return vars;
	}

	public boolean isNumeric(String s) {
		return s.matches("[-+]?\\d*\\.?\\d+");
	}

}
