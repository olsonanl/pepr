
package edu.vt.vbi.ci.pepr.tree.pipeline;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.Random;

<<<<<<< HEAD
import org.apache.log4j.Logger;

import edu.vt.vbi.ci.pepr.alignment.SequenceAlignment;
import edu.vt.vbi.ci.pepr.tree.FastTreeRunner;
import edu.vt.vbi.ci.pepr.tree.RAxMLRunner;
import edu.vt.vbi.ci.util.CommandResults;
import edu.vt.vbi.ci.util.HandyConstants;

/**
 * This class handles building/estimating phylogenetic trees.
 * The main method uses MrBayes, via the MrBayesMonitor
 * and MrBayesMonitorRunner classes. This can be run on a remote
 * host if one is provided. This requires MrBayes to be installed
 * on the host, as well as MrBayesMonitor and MrBayesMonitorRunner.
 * 
 * @author enordber
 *
 */
public class PhylogeneticTreeBuilder implements Runnable{

	private boolean debug = false;
	public static final String MR_BAYES = "MrBayes";

	/**
	 * The remoteWorkingDirectory on the Remote Host must
	 * contain the MrBayesMonitorRunner.jar.
	 */
	private SequenceAlignment alignment;
	private Properties configProperties;
	private String configFileName;
	private String runName;

	private String treeBuildingMethod = MR_BAYES;
	private String mlMatrix;
	private String treeString;
	private int processes = 1;
	private int bootstrapReps = 100;
	private boolean useRaxmlBranchLengths = false;
	private boolean nucleotide = false;
	private boolean useTaxonNames = true;
	private Logger logger = Logger.getLogger(getClass());

	private String constraintTree;

	public PhylogeneticTreeBuilder() {

		String debugProp = System.getProperty(HandyConstants.DEBUG);
		if(debugProp != null && debugProp.equals(HandyConstants.TRUE)) {
			debug = true;
		}

		String rseedProp = System.getProperty("random_seed", ""+System.currentTimeMillis());
		long rseed = Long.parseLong(rseedProp);
		logger.info("PhylogeneticTreeBuilder using random number seed " + rseed);
		Random random = new Random(rseed);
		configFileName = "mbm_config_" + Math.abs(random.nextInt());

		setDefaultConfigurationProperties();
		addProperties(System.getProperties());

		String bootstrapProp = System.getProperty(HandyConstants.SUPPORT_REPS);
		int bs = bootstrapReps;
		if(bootstrapProp != null) {
			try {
				bs = Integer.parseInt(bootstrapProp);
			} catch(NumberFormatException nfe) {
				logger.info("Value for " + HandyConstants.SUPPORT_REPS +
						" must be an integer, not: " + bootstrapProp);
				logger.info("Using default bootstrap value of " + bootstrapReps);
				bs = bootstrapReps;
			}
			setBootstrapReps(bs);
		}
		
	}

	/**
	 * Runnable interface method. This method simply calls the 
	 * buildMrBayesTree() method. A MrBayesResultSet should be 
	 * available via getMrBayesResultSet() after run() exits.
	 */
	public void run() {
		if(treeBuildingMethod.equals(HandyConstants.MAXIMUM_LIKELIHOOD)) {
			if(debug) {
				System.out.println("PhylogeneticTreeBuilder.run() " +
				"build ML raxml tree");
			}
			buildRaxmlTree();
		} else if(treeBuildingMethod.equals(HandyConstants.PARSIMONY)) {
			if(debug) {
				System.out.println("PhylogeneticTreeBuilder.run() " +
				"build raxml parsimony tree");
			}
			buildRaxmlParsimonyTree();
		} else if(treeBuildingMethod.equals(HandyConstants.PARSIMONY_BL)) {
			buildRaxmlParsimonyTreeWithBL();
		} else if(treeBuildingMethod.equals(HandyConstants.FAST_TREE)) {
			buildFastTree();
		}
	}

	/**
	 * Runs RAxML on the RemoteHost (which is the localhost
	 * by default), using the current RAxML parameters, building
	 * only the parsimony starting tree.
	 */
	private void buildRaxmlParsimonyTree() {
		if(debug) {
			System.out.println("PhylogeneticTreeBuilder.buildRaxmlParsimonyTree()");
		}
		RAxMLRunner raxmlRunner = new RAxMLRunner(getProcesses());
		raxmlRunner.setParsimonyOnly(true);
		raxmlRunner.setBootstrapReps(getBootstrapReps());
		raxmlRunner.setAlignment(getAlignment());
		raxmlRunner.run();
		setTreeString(raxmlRunner.getParsimonyTree());
	}

	/**
	 * Runs RAxML twice on the RemoteHost (which is the localhost
	 * by default), using the current RAxML parameters, building
	 * the parsimony starting tree the first run, and determining ML branch
	 * lengths the second run.
	 */
	private void buildRaxmlParsimonyTreeWithBL() {
		RAxMLRunner raxmlRunner = new RAxMLRunner(getProcesses());
		raxmlRunner.setParsimonyWithBL(true);
		raxmlRunner.setBootstrapReps(getBootstrapReps());
		raxmlRunner.setAlignment(getAlignment());
		raxmlRunner.run();
		setTreeString(raxmlRunner.getParsimonyWithBLTree());
	}


	/**
	 * Runs RAxML on the RemoteHost (which is the localhost
	 * by default), using the current RAxML parameters.
	 */
	private void buildRaxmlTree() {
		RAxMLRunner raxmlRunner = new RAxMLRunner(getProcesses());
		raxmlRunner.setBootstrapReps(getBootstrapReps());
		raxmlRunner.setAlignment(getAlignment());
		raxmlRunner.setMatrix(mlMatrix);
		raxmlRunner.setUseTaxonNames(useTaxonNames());
		raxmlRunner.run();
		if(getBootstrapReps() == 0) {
			setTreeString(raxmlRunner.getBestTree());
		} else {
			setTreeString(raxmlRunner.getBestTreeWithSupports());
		}

	}

	private void buildFastTree() {
		FastTreeRunner ftr = new FastTreeRunner();
		ftr.setNucleoide(nucleotide);
		ftr.setAlignment(getAlignment());
		ftr.setRunName(getRunName());
		ftr.setUseRaxmlBranchLengths(useRaxmlBranchLengths);
		if(constraintTree != null) {
			ftr.setConstraintTree(constraintTree);
		}
		ftr.run();
		String tree = ftr.getResult();
		setTreeString(tree);
	}

	public SequenceAlignment getAlignment() {
		return alignment;
	}

	public void setAlignment(SequenceAlignment alignment) {
		this.alignment = alignment;

		try {
			File alignmentTempFile = new File(System.getProperty("user.dir") 
					+ System.getProperty("file.separator") 
					+ alignment.getName());
			alignmentTempFile = File.createTempFile("tmp_", ".align", 
					new File(System.getProperty("user.dir")));
			alignmentTempFile.deleteOnExit();
			FileWriter fw = new FileWriter(alignmentTempFile);
			fw.write(alignment.getAlignmentAsFasta());
			fw.flush();
			fw.close();

			//set alignment file name property
			String alignmentFileName = alignmentTempFile.getName();
			setConfigProperty(HandyConstants.ALIGN_FILE, alignmentFileName);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/**
	 * Sets a property for the configuration file that will be used
	 * by MrBayesMonitorRunner on the remote host. This file includes
	 * properties such as MrBayes executable location, input alignment
	 * file name, and various parameters for the MrBayes run.
	 */
	public void setConfigProperty(String key, String value) {
		configProperties.setProperty(key, value);
	}

	private void setDefaultConfigurationProperties() {
		configProperties = new Properties();

		configProperties.setProperty(HandyConstants.ALIGN_FILE, "align.in");
		configProperties.setProperty(HandyConstants.GEN_PER_ROUND, "1000");
		configProperties.setProperty(HandyConstants.ALIGN_FORMAT, 
				HandyConstants.FASTA);
		configProperties.setProperty(HandyConstants.MIN_PRE_BURNIN, "1000");
		configProperties.setProperty(HandyConstants.MIN_POST_BURNIN, "10000");
		configProperties.setProperty(HandyConstants.SAMPLE_FREQ, "100");
		configProperties.setProperty(HandyConstants.TOPOLOGY_TYPE, 
				HandyConstants.VARIABLE_TOPLOGY);
		configProperties.setProperty(HandyConstants.NRUNS, "1");
		configProperties.setProperty(HandyConstants.MB_PATH, "/usr/bin/mb");
=======
import edu.vt.vbi.ci.pepr.alignment.SequenceAlignment;
import edu.vt.vbi.ci.pepr.tree.FastTreeRunner;
import edu.vt.vbi.ci.pepr.tree.RAxMLRunner;
import edu.vt.vbi.ci.util.CommandResults;
import edu.vt.vbi.ci.util.HandyConstants;
import edu.vt.vbi.ci.util.RemoteHost;

/**
 * This class handles building/estimating phylogenetic trees.
 * The main method uses MrBayes, via the MrBayesMonitor
 * and MrBayesMonitorRunner classes. This can be run on a remote
 * host if one is provided. This requires MrBayes to be installed
 * on the host, as well as MrBayesMonitor and MrBayesMonitorRunner.
 * 
 * @author enordber
 *
 */
public class PhylogeneticTreeBuilder implements Runnable{

	private boolean debug = false;
	public static final String MR_BAYES = "MrBayes";


	private RemoteHost host;

	/**
	 * The remoteWorkingDirectory on the Remote Host must
	 * contain the MrBayesMonitorRunner.jar.
	 */
	private String remoteWorkingDirectory;
	private String remoteUserName;
	private SequenceAlignment alignment;
	private Properties configProperties;
	private String configFileName;
	private String runName;
	private String jarFileName = 
		"PhylogenomicPipeline.jar";

	private String treeBuildingMethod = MR_BAYES;
	private String mlMatrix;
	private String treeString;
	private int processes = 1;
	private int bootstrapReps = 100;
	private boolean useRaxmlBranchLengths = false;
	private boolean nucleotide = false;
	private boolean useTaxonNames = true;

	private String constraintTree;

	public PhylogeneticTreeBuilder() {
		remoteUserName = System.getProperty("user.name");
		remoteWorkingDirectory = System.getProperty("user.dir").trim();

		String debugProp = System.getProperty(HandyConstants.DEBUG);
		if(debugProp != null && debugProp.equals(HandyConstants.TRUE)) {
			debug = true;
		}

		host = RemoteHost.getLocalHost();
		host.setRemoteJavaPath(host.getCommandPath("java"));
		host.setRemoteWorkingDirectory(remoteWorkingDirectory);
		Random random = new Random();
		configFileName = "mbm_config_" + Math.abs(random.nextInt());

		setDefaultConfigurationProperties();
		addProperties(System.getProperties());

		String bootstrapProp = System.getProperty(HandyConstants.SUPPORT_REPS);
		if(bootstrapProp != null) {
			try {
				int bs = Integer.parseInt(bootstrapProp);
				setBootstrapReps(bs);
			} catch(NumberFormatException nfe) {
				System.out.println("Value for " + HandyConstants.SUPPORT_REPS +
						" must be an integer, not: " + bootstrapProp);
			}
		}
	}

	/**
	 * Runnable interface method. This method simply calls the 
	 * buildMrBayesTree() method. A MrBayesResultSet should be 
	 * available via getMrBayesResultSet() after run() exits.
	 */
	public void run() {
		if(treeBuildingMethod.equals(HandyConstants.MAXIMUM_LIKELIHOOD)) {
			if(debug) {
				System.out.println("PhylogeneticTreeBuilder.run() " +
				"build ML raxml tree");
			}
			buildRaxmlTree();
		} else if(treeBuildingMethod.equals(HandyConstants.PARSIMONY)) {
			if(debug) {
				System.out.println("PhylogeneticTreeBuilder.run() " +
				"build raxml parsimony tree");
			}
			buildRaxmlParsimonyTree();
		} else if(treeBuildingMethod.equals(HandyConstants.PARSIMONY_BL)) {
			buildRaxmlParsimonyTreeWithBL();
		} else if(treeBuildingMethod.equals(HandyConstants.FAST_TREE)) {
			buildFastTree();
		}
	}

	/**
	 * Runs RAxML on the RemoteHost (which is the localhost
	 * by default), using the current RAxML parameters, building
	 * only the parsimony starting tree.
	 */
	private void buildRaxmlParsimonyTree() {
		if(debug) {
			System.out.println("PhylogeneticTreeBuilder.buildRaxmlParsimonyTree()");
		}
		RAxMLRunner raxmlRunner = new RAxMLRunner(getProcesses());
		raxmlRunner.setHost(host);
		raxmlRunner.setParsimonyOnly(true);
		raxmlRunner.setBootstrapReps(getBootstrapReps());
		raxmlRunner.setAlignment(getAlignment());
		raxmlRunner.run();
		setTreeString(raxmlRunner.getParsimonyTree());
	}

	/**
	 * Runs RAxML twice on the RemoteHost (which is the localhost
	 * by default), using the current RAxML parameters, building
	 * the parsimony starting tree the first run, and determining ML branch
	 * lengths the second run.
	 */
	private void buildRaxmlParsimonyTreeWithBL() {
		if(debug) {
			System.out.println("PhylogeneticTreeBuilder.buildRaxmlParsimonyTreeWithBL()");
		}
		RAxMLRunner raxmlRunner = new RAxMLRunner(getProcesses());
		raxmlRunner.setHost(host);
		raxmlRunner.setParsimonyWithBL(true);
		raxmlRunner.setBootstrapReps(getBootstrapReps());
		raxmlRunner.setAlignment(getAlignment());
		raxmlRunner.run();
		setTreeString(raxmlRunner.getParsimonyWithBLTree());
	}


	/**
	 * Runs RAxML on the RemoteHost (which is the localhost
	 * by default), using the current RAxML parameters.
	 */
	private void buildRaxmlTree() {
		if(debug) {
			System.out.println("PhylogeneticTreeBuilder.buildRaxmlTree()");
		}
		RAxMLRunner raxmlRunner = new RAxMLRunner(getProcesses());
		raxmlRunner.setHost(host);
		raxmlRunner.setBootstrapReps(getBootstrapReps());
		raxmlRunner.setAlignment(getAlignment());
		raxmlRunner.setMatrix(mlMatrix);
		raxmlRunner.setUseTaxonNames(useTaxonNames());
		raxmlRunner.run();
		if(getBootstrapReps() == 0) {
			setTreeString(raxmlRunner.getBestTree());
		} else {
			setTreeString(raxmlRunner.getBestTreeWithSupports());
		}

	}

	private void buildFastTree() {
		if(debug) {
			System.out.println("PhylogeneticTreeBuilder.buildFastTree()");
		}
		FastTreeRunner ftr = new FastTreeRunner();
		ftr.setNucleoide(nucleotide);
		ftr.setAlignment(getAlignment());
		ftr.setRunName(getRunName());
		ftr.setUseRaxmlBranchLengths(useRaxmlBranchLengths);
		if(constraintTree != null) {
			ftr.setConstraintTree(constraintTree);
		}
		ftr.run();
		String tree = ftr.getResult();
		setTreeString(tree);
	}

	public SequenceAlignment getAlignment() {
		return alignment;
	}

	public void setAlignment(SequenceAlignment alignment) {
		if(debug) {
			System.out.println("PhylogeneticTreeBuilder.setAlignment() "
					+ alignment.getName());
		}

		this.alignment = alignment;

		try {
			File alignmentTempFile = new File(System.getProperty("user.dir") 
					+ System.getProperty("file.separator") 
					+ alignment.getName());
			alignmentTempFile = File.createTempFile("tmp_", ".align", 
					new File(System.getProperty("user.dir")));
			alignmentTempFile.deleteOnExit();
			FileWriter fw = new FileWriter(alignmentTempFile);
			fw.write(alignment.getAlignmentAsFasta());
			fw.flush();
			fw.close();

			//set alignment file name property
			String alignmentFileName = alignmentTempFile.getName();
			setConfigProperty(HandyConstants.ALIGN_FILE, alignmentFileName);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/**
	 * Sets a property for the configuration file that will be used
	 * by MrBayesMonitorRunner on the remote host. This file includes
	 * properties such as MrBayes executable location, input alignment
	 * file name, and various parameters for the MrBayes run.
	 */
	public void setConfigProperty(String key, String value) {
		configProperties.setProperty(key, value);
	}

	private void setDefaultConfigurationProperties() {
		configProperties = new Properties();

		configProperties.setProperty(HandyConstants.ALIGN_FILE, "align.in");
		configProperties.setProperty(HandyConstants.GEN_PER_ROUND, "1000");
		configProperties.setProperty(HandyConstants.ALIGN_FORMAT, 
				HandyConstants.FASTA);
		configProperties.setProperty(HandyConstants.MIN_PRE_BURNIN, "1000");
		configProperties.setProperty(HandyConstants.MIN_POST_BURNIN, "10000");
		configProperties.setProperty(HandyConstants.SAMPLE_FREQ, "100");
		configProperties.setProperty(HandyConstants.TOPOLOGY_TYPE, 
				HandyConstants.VARIABLE_TOPLOGY);
		configProperties.setProperty(HandyConstants.NRUNS, "1");
		configProperties.setProperty(HandyConstants.MB_PATH, "/usr/bin/mb");
	}

	private void writeConfigFile(String fileName) throws IOException {
		if(configProperties != null) {
			System.out.println("PhylogeneticTreeBuilder.writeConfigProperties() " +
					"configProperties is not null, write to file: " + fileName);
			FileOutputStream fos = new FileOutputStream(fileName);
			configProperties.store(fos, "Configuration File for" +
			" MrBayesMonitorRunner");
			fos.close();
		} else {
			System.out.println("PhylogeneticTreeBuilder.writeConfigProperties() " +
			"configProperties is null");
		}
	}

	public RemoteHost getHost() {
		return host;
	}

	public void setHost(RemoteHost host) {
		this.host = host;
>>>>>>> refs/remotes/origin/master
	}

	public String getConfigFileName() {
		return configFileName;
	}

	public void setConfigFileName(String configFileName) {
		this.configFileName = configFileName;
	}

	public synchronized void addProperties(Properties properties) {
		configProperties.putAll(properties);
	}

	String getTreeBuildingMethod() {
		return treeBuildingMethod;
	}

	public void setTreeBuildingMethod(String treeBuildingMethod) {
		if(debug) {
			System.out.println("PhylogeneticTreeBuilder.setTreeBuildingMethod()" +
					" to " + treeBuildingMethod);
		}
		this.treeBuildingMethod = treeBuildingMethod;
	}

	public String getTreeString() {
		return treeString;
	}

	public void setTreeString(String treeString) {
		this.treeString = treeString;
	}

	/**
	 * Sets the number of processes to be run to build this 
	 * tree. This is used in combination with bootstrapping in RAxML.
	 * The bootstrap replicates are divided among the different
	 * processes. The RAxML_bootstrap file contents are collected
	 * and written to a single file, containing all bootstrap trees.
	 * The PHYLIP program consense is used to get the consensus tree,
	 * with support values.
	 * @param procs
	 */
	public void setProcesses(int procs) {
		processes = procs;		
	}

	private int getProcesses() {
		return processes;
	}

	public void setBootstrapReps(int reps) {
		bootstrapReps = reps;
	}

	public int getBootstrapReps() {
		return bootstrapReps;
	}

	public void setMLMatrix(String mlMatrix) {
		this.mlMatrix = mlMatrix;
	}

	public void setConstraintTree(String treeString) {
		constraintTree = treeString;
	}

	public void setRunName(String runName) {
		this.runName = runName;
	}

	public String getRunName(){
		return runName;
	}

	public void useRaxmlBranchLengths(boolean b) {
		this.useRaxmlBranchLengths = b;
	}
	
	public void setNucleotide(boolean nuc) {
		nucleotide = nuc;
	}
	
	public boolean useTaxonNames() {
		return useTaxonNames;
	}

	public void setUseTaxonNames(boolean useTaxonNames) {
		this.useTaxonNames = useTaxonNames;
	}


}
