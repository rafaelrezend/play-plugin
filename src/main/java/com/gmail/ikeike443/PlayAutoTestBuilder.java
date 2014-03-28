package com.gmail.ikeike443;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.BuildListener;
import hudson.model.ParameterValue;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.ParametersAction;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author ikeike443
 */
public class PlayAutoTestBuilder extends Builder {

	private final String projectPath;
	private final String addParameters;
	private final String play_cmd;
	private final List<String> play_cmds;
	private final String play_path;

	@SuppressWarnings("serial")
	@DataBoundConstructor
	public PlayAutoTestBuilder(final String projectPath,
			final String addParameters, final String play_cmd,
			final String play_path) {
		System.out.println("Creating play auto test builder");
		this.projectPath = projectPath;
		this.addParameters = addParameters;
		this.play_cmd = play_cmd;

		this.play_cmds = new ArrayList<String>() {
			{
				add(projectPath);
				add(addParameters);
				add(play_cmd);
			}
		};
		System.out.println("Commands: " + play_cmds);
		this.play_path = play_path;
	}

	public String getProjectPath() {
		return projectPath;
	}

	public String getAddParameters() {
		return addParameters;
	}

	public List<String> getPlay_cmds() {
		return play_cmds;
	}

	public String getPlay_cmd() {
		return play_cmd;
	}

	public String getPlay_path() {
		return play_path;
	}

	@SuppressWarnings({ "deprecation" })
	@Override
	public boolean perform(@SuppressWarnings("rawtypes") AbstractBuild build,
			Launcher launcher, BuildListener listener) {
		// clean up
		try {
			FilePath[] files = build.getProject().getWorkspace()
					.list("test-result/*");

			for (FilePath filePath : files) {
				filePath.delete();
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

		// This maps stored the executed commands and the results
		Map<String, String> exitcodes = new HashMap<String, String>();

		// build playpath
		String playpath = null;
		if (play_path != null && play_path.length() > 0) {
			playpath = play_path;
		} else if (getDescriptor().path() != null) {
			playpath = getDescriptor().path();
		} else {
			listener.getLogger().println("play path is null");
			return false;
		}

		listener.getLogger().println("play path is " + playpath);

		FilePath workDir = build.getWorkspace();
		@SuppressWarnings("unchecked")
		PlayAutoTestJobProperty playJobProperty = (PlayAutoTestJobProperty) build
				.getProject().getProperty(PlayAutoTestJobProperty.class);
		String application_path = playJobProperty != null ? playJobProperty
				.getApplicationPath() : null;
		if (application_path != null && application_path.length() > 0) {
			workDir = build.getWorkspace().child(application_path);
		}

		try {
			for (String play_cmd : this.play_cmds) {
				if (play_cmd != null && play_cmd.length() == 0)
					continue;

				// Substitute parameters
				ParametersAction param = build
						.getAction(hudson.model.ParametersAction.class);
				if (param != null) {
					listener.getLogger().println(
							"Substituting job parameters from " + play_cmd);
					List<ParameterValue> values = param.getParameters();
					if (values != null) {
						for (ParameterValue value : values) {
							String v = value.createVariableResolver(build)
									.resolve(value.getName());
							play_cmd = play_cmd.replace("${" + value.getName()
									+ "}", v);
						}
					}
				}

				String[] cmds = play_cmd.split(" ", 2);
				String cmd = playpath + " " + cmds[0] + " \""
						+ workDir.toString() + "\" "
						+ (cmds.length >= 2 ? cmds[1] : "");

				listener.getLogger().println("Executing " + cmd);
				// Proc proc = launcher.launch(cmd, new
				// String[0],listener.getLogger(),workDir);
				// int exitcode = proc.join();

				Proc proc = launcher.launch().cmds(playpath, "test")
						.pwd(workDir).writeStdin().stdout(listener.getLogger())
						.stderr(listener.getLogger()).start();
				PrintStream ps = new PrintStream(proc.getStdin());

				// Proc proc =
				// launcher.launch().cmds(cmds).envs(env).writeStdin().stdout(listener.getLogger()).stderr(listener.getLogger()).start();
				// PrintStream ps = new PrintStream(proc.getStdin());

				// ps.println(call);
				// ps.flush();
				//
				// ps.println("echo %errorlevel%");
				// ps.flush();
				//
				// ps.println("exit %errorlevel%");
				// ps.flush();

				int exitcode = proc.join();
				//
				System.out.println("EXITCODE########### " + exitcode);

				exitcodes.put(play_cmd, (exitcode == 0 ? "Done" : "Fail"));

				if (exitcode != 0) {
					listener.getLogger()
							.println(
									"****************************************************");
					listener.getLogger().println(
							"* ERROR!!! while executing " + play_cmd);
					listener.getLogger()
							.println(
									"****************************************************");
					return false;
				}

				if (play_cmd != null && play_cmd.matches("(auto-test.*)")) {
					// check test-result
					if (!new FilePath(workDir, "test-result/result.passed")
							.exists()) {
						build.setResult(Result.UNSTABLE);
					}
				}
			}

			listener.getLogger().println("Each commands' results:");
			for (Map.Entry<String, String> rec : exitcodes.entrySet()) {
				listener.getLogger().println(
						"  " + rec.getKey() + ": " + rec.getValue());
			}
			return exitcodes.containsValue("Fail") ? false : true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	/**
	 * Descriptor for {@link PlayAutoTestBuilder}. Used as a singleton. The
	 * class is marked as public so that it can be accessed from views.
	 * 
	 * <p>
	 * See <tt>views/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
	 * for the actual HTML fragment for the configuration screen.
	 */
	@Extension
	// this marker indicates Hudson that this is an implementation of an
	// extension point.
	public static final class DescriptorImpl extends
			BuildStepDescriptor<Builder> {
		public DescriptorImpl() {
			super();
			load();
		}

		/**
		 * To persist global configuration information, simply store it in a
		 * field and call save().
		 * 
		 * <p>
		 * If you don't want fields to be persisted, use <tt>transient</tt>.
		 */
		private String path;

		/**
		 * Performs on-the-fly validation of the form field 'name'.
		 * 
		 * @param value
		 *            This parameter receives the value that the user has typed.
		 * @return Indicates the outcome of the validation. This is sent to the
		 *         browser.
		 */
		public FormValidation doCheckName(@QueryParameter String value)
				throws IOException, ServletException {
			if (value.length() == 0)
				return FormValidation.error("Please set path to play");

			return FormValidation.ok();
		}

		public boolean isApplicable(
				@SuppressWarnings("rawtypes") Class<? extends AbstractProject> aClass) {
			// indicates that this builder can be used with all kinds of project
			// types
			return true;
		}

		/**
		 * This human readable name is used in the configuration screen.
		 */
		public String getDisplayName() {
			return "Invoke Play!Framework";
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData)
				throws FormException {
			path = formData.getString("play_path");
			save();
			return super.configure(req, formData);
		}

		/**
		 * This method returns true if the global configuration says we should
		 * speak French.
		 */
		public String path() {
			return path;
		}
	}
}
