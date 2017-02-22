package org.ciat.dssat_sum.model;

import java.util.List;

public class Section {
	
	private String name;
	private List<Variable> variables;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<Variable> getVariables() {
		return variables;
	}

	public void setVariables(List<Variable> variables) {
		this.variables = variables;
	}
}
