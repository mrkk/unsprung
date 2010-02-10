package org.kered.unsprung;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


public class UnSprung {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		Map<String,SpringContextBean> beanMap = new HashMap<String,SpringContextBean>();
		Properties props = new Properties();
		
		for( int i=0; i<args.length; ++i ) {
			String arg = args[i];
			if( "-p".equals(arg) ) {
				++i;
				System.err.println("loading properties: "+ args[i]);
				try {
					props.load(new FileInputStream(args[i]));
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				System.err.println("loading context: "+ arg);
				extractBeansFromFile( arg, beanMap );
			}
		}
		
		autowire(beanMap);
		List<SpringContextBean> emittedBeans = new ArrayList<SpringContextBean>();
		emitter(beanMap, null, emittedBeans, null);
		writeCode(emittedBeans, props);
		
	}
	
	private static void autowire(Map<String, SpringContextBean> beanMap) {
		for( Entry<String, SpringContextBean> entry : beanMap.entrySet() ) {
			String beanName = entry.getKey();
			SpringContextBean bean = entry.getValue();
			if( "constructor".equals(bean.autowire) ) {
				try {
					System.out.println("autowiring: "+ beanName +" ("+ bean.className +")");
					Class c = ClassLoader.getSystemClassLoader().loadClass(bean.className);
					for( Constructor c2 : c.getConstructors() ) {
						//System.out.println("found constructor: "+ c2 +" "+ c2.isAccessible());
						Class[] paramTypes = c2.getParameterTypes();
						if( paramTypes.length==0 ) continue;
						for( int i=0; i<paramTypes .length; ++i ) {
							Class paramType = paramTypes [i];
							System.out.print("\t"+ paramType +": ");
							boolean foundMatch = false;
							for( Entry<String, SpringContextBean> entry2 : beanMap.entrySet() ) {
								if( entry2.getValue().className.equals(paramType.getName()) ) {
									bean.addConstructorArg(i, entry2.getKey());
									foundMatch = true;
									System.out.println("matching bean - "+ entry2.getKey());
									break;
								}
							}
							if( !foundMatch ) {
								System.out.println("no matching bean found");
								bean.addConstructorArgValue(i, "null");
							}
						}
						break;
					}
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private static void extractBeansFromFile(String fn, Map<String, SpringContextBean> beanMap) {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db;
		try {
			db = dbf.newDocumentBuilder();
			Document doc = db.parse(new File(fn));
			doc.getDocumentElement().normalize();
			NodeList nodeList = doc.getElementsByTagName("bean");
			for( int i=0; i<nodeList.getLength(); ++i ) {
				Node node = nodeList.item(i);
				SpringContextBean bean = new SpringContextBean();
				bean.srcFileName = fn;
				if( !((Element) node).hasAttribute("id") ) bean.name = genName(node);
				else bean.name = ((Element) node).getAttribute("id");
				bean.className = ((Element) node).getAttribute("class");
				beanMap.put(bean.name, bean);
				
				// note autowire
				if( ((Element) node).hasAttribute("autowire") ) {
					bean.autowire = ((Element) node).getAttribute("autowire");
				}
				
				// handle constructor-args
				NodeList nodeList2 = ((Element) node).getChildNodes();
				for( int j=0; j<nodeList2.getLength(); ++j ) {
					Node node2 = nodeList2.item(j);
					if( "constructor-arg".equals(node2.getNodeName()) ) {
						Element constArg = (Element) node2;
						if( constArg.hasAttribute("ref") ) {
							bean.addConstructorArg(new Integer(constArg.getAttribute("index")), constArg.getAttribute("ref"));
						} else if( constArg.hasAttribute("value") ) {
							bean.addConstructorArgValue(new Integer(constArg.getAttribute("index")), constArg.getAttribute("value"));
						} else {
							NodeList nodeList3 = constArg.getChildNodes();
							for( int k=0; k<nodeList3.getLength(); ++k ) {
								Node node3 = nodeList3.item(k);
								if( "bean".equals(node3.getNodeName()) ) {
									bean.addConstructorArg(new Integer(constArg.getAttribute("index")), genName(node3));
								}
							}
						}
					}
					if( "property".equals(node2.getNodeName()) ) {
						Element constArg = (Element) node2;
						if( constArg.hasAttribute("ref") ) {
							bean.addProperty(constArg.getAttribute("name"), constArg.getAttribute("ref"));
						} else if( constArg.hasAttribute("value") ) {
							bean.addPropertyValue(constArg.getAttribute("name"), constArg.getAttribute("value"));
						} else {
							NodeList nodeList3 = constArg.getChildNodes();
							for( int k=0; k<nodeList3.getLength(); ++k ) {
								Node node3 = nodeList3.item(k);
								if( "bean".equals(node3.getNodeName()) ) {
									bean.addProperty(constArg.getAttribute("name"), genName(node3));
								}
							}
						}
					}
				}
				
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}

	private static String genName(Node node) {
		StringBuffer sb = new StringBuffer();
		while( node!=null ) {
			sb.append("_");
			sb.append(new Integer(node.hashCode()).toString());
			node = node.getParentNode();
		}
		return sb.toString();
	}

	private static String getVarName(String name) {
		if( name.startsWith("_" )) return "bean"+name;
		else return "bean_"+name;
	}

	private static String getSetterName(String name) {
		return "set"+ (name.toUpperCase().charAt(0)) + name.substring(1);
	}

	private static void writeCode(List<SpringContextBean> emittedBeans, Properties props) {
		try {
			BufferedWriter bw = new BufferedWriter( new FileWriter("GeneratedSpringContext.java") );
			bw.write("\n// generated by UnSprung\n\n");
			bw.write("public class GeneratedSpringContext {\n\n");

			// write instance variables
			for( SpringContextBean bean : emittedBeans ) {
				bw.write("\t"+ bean.className +" "+ bean.getVarName() +";\t// "+ bean.srcFileName +"\n" );
			}

			// write GeneratedSpringContext constructor
			bw.write("\n\tpublic GeneratedSpringContext() {\n\n");
			bw.write("\t\ttry {\n\n");
			for( SpringContextBean bean : emittedBeans ) {
				
				// write constructor
				bw.write("\t\t\t"+ bean.getVarName() +" = new "+ bean.className +"( " );
				List<String> deps = bean.getConstructorDeps();
				List<String> values = bean.getConstructorValues();
				for( int i=0; i<bean.getConstructorArgSize(); ++i ) {
					if( i<deps.size() && deps.get(i)!=null ) {
						String dep = deps.get(i);
						bw.write(getVarName(dep));
					}
					if( i<values.size() && values.get(i)!=null ) {
						bw.write(stringify(values.get(i), props));
					}
					if( i<bean.getConstructorArgSize()-1 ) bw.write(", ");
					else bw.write(" ");
				}
				
				bw.write(");\n");
				
				if( !bean.getPropertyDeps().isEmpty() || !bean.getPropertyValues().isEmpty() ) {
					// write setters
					for( Entry<String,String> entry : bean.getPropertyDeps().entrySet() ) {
						bw.write("\t\t\t"+ bean.getVarName() +"."+ getSetterName(entry.getKey()) +"( "+ getVarName(entry.getValue()) +" );\n");
					}
					for( Entry<String,String> entry : bean.getPropertyValues().entrySet() ) {
						
						bw.write("\t\t\t"+ bean.getVarName() +"."+ getSetterName(entry.getKey()) +"( "+ stringify(entry.getValue(), props) +" );\n");
					}
				}
			}

			bw.write("\n\t\t} catch(Throwable t) {\n\t\t\tt.printStackTrace();\n\t\t}\n\n");
			bw.write("\t}\n");
			bw.write("}\n");
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static String stringify(String value, Properties props) {
		if( value==null || "null".equals(value) ) return "null";
		if( value.startsWith("$") ) {
			value = value.substring(1);
			if( value.startsWith("{") ) value = value.substring(1);
			if( value.endsWith("}") ) value = value.substring(0,value.length()-1);
			if( props.containsKey(value) ) {
				value = props.getProperty(value);
			}
		}
		if( value.startsWith("file:") ) return "new org.springframework.core.io.FileSystemResource(\""+ value.substring(5) +"\")";
		if( "true".equals(value) || "false".equals(value) ) return value;
		if( "SECONDS".equals(value) ) return "java.util.concurrent.TimeUnit.SECONDS"; //HACK
		try { return new Integer(value).toString(); } 
		catch(Exception e) { /* ignore */ }
		return "\"" + value + "\"";
	}

	public static void emitter(Map<String,SpringContextBean> beanMap, Collection<String> beanNames, List<SpringContextBean> emittedBeans, Set<String> visited ) {
		if( beanNames==null ) beanNames = beanMap.keySet();
		if( visited==null ) visited = new HashSet<String>();
		for( String beanName : beanNames ) {
			if( beanMap.containsKey(beanName) ) {
				if( visited.contains(beanName) ) continue; // throw new RuntimeException("cycle detected - "+ beanName);
				visited.add(beanName);
				SpringContextBean bean = beanMap.get(beanName);
				emitter(beanMap, bean.getDeps(), emittedBeans, visited);
				System.out.println("emitting: "+ beanName +" ("+ bean.className +")");
				emittedBeans.add(bean);
			} else {
				System.err.println("warning: unknown bean reference - "+ beanName);
			}
		}
	}
	
	private static class SpringContextBean {
		public String srcFileName;
		String name = null;
		String className = null;
		List<String> constructorArgs = new ArrayList<String>();
		List<String> constructorArgValues = new ArrayList<String>();
		Map<String,String> properties = new HashMap<String,String>();
		Map<String,String> propertyValues = new HashMap<String,String>();
		String autowire = null;
		
		public void addConstructorArg(int index, String bean) {
			while( index >= constructorArgs.size() ) {
				constructorArgs.add(null);
			}
			constructorArgs.set(index, bean);
		}
		
		public List<String> getConstructorValues() {
			return new ArrayList<String>(constructorArgValues);
		}

		public void addConstructorArgValue(int index, String value) {
			while( index >= constructorArgValues.size() ) {
				constructorArgValues.add(null);
			}
			constructorArgValues.set(index, value);
		}
		
		public int getConstructorArgSize() {
			return constructorArgs.size() > constructorArgValues.size() ? constructorArgs.size() : constructorArgValues.size();
		}
		
		public void addPropertyValue(String prop, String value) {
			propertyValues.put(prop, value);
		}

		public void addProperty(String prop, String bean) {
			properties.put(prop, bean);
		}
		
		public List<String> getDeps() {
			List<String> deps = new ArrayList<String>(constructorArgs);
			deps.addAll(properties.values());
			return deps;
		}

		public List<String> getConstructorDeps() {
			List<String> deps = new ArrayList<String>(constructorArgs);
			return deps;
		}
		
		public Map<String,String> getPropertyDeps() {
			return new HashMap<String,String>( this.properties );
		}
		
		public Map<String,String> getPropertyValues() {
			return new HashMap<String,String>( this.propertyValues );
		}
		
		public String getVarName() {
			return UnSprung.getVarName(name);
		}

}

}
