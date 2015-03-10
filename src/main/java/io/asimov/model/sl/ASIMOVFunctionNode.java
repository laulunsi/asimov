package io.asimov.model.sl;

import io.asimov.reasoning.sl.KBase;
import io.coala.json.JsonUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class ASIMOVFunctionNode implements ASIMOVNode<ASIMOVFunctionNode> {
	
	public static final String CARDINALITY = "CARDINALITY";

	
	public String functionType;
	
	public Map<String,ASIMOVNode<?>> namedChildren;
	
	public String name;

	public ASIMOVFunctionNode(){
		super();
		getNamedChildren();
	}
	
	public Map<String, ASIMOVNode<?>> getNamedChildren() {
		if (this.namedChildren == null)
			this.namedChildren = new HashMap<String, ASIMOVNode<?>>();
		return namedChildren;
	}

	public String resultKey;
	
	@JsonIgnore
	public ASIMOVNode<?> result;
	
	@JsonIgnore
	public String type = getNodeType();
	
	public ASIMOVFunctionNode(ASIMOVNode<?>... formulas) {
		int count = 0;
		if (getNamedChildren() != null)
			count = namedChildren.size();
		if (formulas != null)
			for (ASIMOVNode<?> f : formulas) {
				this.instantiate(""+(count++), f);
			}
				
	}
	
	
	public void eval(ASIMOVNode<?> parameter){
		if (this.getFunctionType() == CARDINALITY) {
			long cardinality = 0;
			for (String key : namedChildren.keySet()) {
				if (new KBase().matchNode(namedChildren.get(key), parameter) != null)
					cardinality++;
			}
			result = SL.integer(cardinality);
		}
	}
	
	public String getFunctionType() {
		return this.functionType;
	}
	
	public String getResultKey() {
		return this.resultKey;
	}
	

	public ASIMOVFunctionNode withResultKey(final String resultKey) {
		this.resultKey = resultKey;
		return this;
	}
	

	public ASIMOVFunctionNode withFunctionType(final String functionType) {
		this.functionType = functionType;
		return this;
	}
	
	@Override
	public String toJSON() {
		return JsonUtil.toJSONString(this);
	}

	@Override
	public ASIMOVFunctionNode fromJSON(String jsonValue) {
		InputStream is = new ByteArrayInputStream(jsonValue.getBytes());
		ASIMOVFunctionNode result = JsonUtil.fromJSON(is,ASIMOVFunctionNode.class);
		try {
			is.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return result;
	}


	@Override
	public ASIMOVNode<ASIMOVFunctionNode> fromXML(Object xmlBean) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object toXML() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <N extends ASIMOVNode<N>> N toSL() {
		return (N) this;
	}

	@Override
	public <N extends ASIMOVNode<N>> ASIMOVNode<ASIMOVFunctionNode> fromSL(N node) {
		this.fromJSON(node.toJSON());
		return this;
	}

	@Override
	public ASIMOVNode<ASIMOVFunctionNode> withName(String name) {
		this.name = name;
		return this;
	}

	@Override
	public String getName() {
		return getFunctionType();
	}

	@Override
	public String getNodeType() {
		return "FUNCTION";
	}

	@Override
	public ASIMOVNode<ASIMOVFunctionNode> instantiate(String key, ASIMOVNode<?> value) {
		if (this.namedChildren == null)
			this.namedChildren = new HashMap<String, ASIMOVNode<?>>();
		this.namedChildren.put(key, value);
		return this;
	}

	@Override
	public Set<String> getKeys() {
		Set<String> keys = namedChildren.keySet();
		keys.add(resultKey);
		return keys;
	}

	@Override
	public Object getPropertyValue(String key) {
		if (key == resultKey)
			return result;
		return namedChildren.get(key);
	}
	
	@Override
	public String toString(){
		return this.toJSON();
	}


}