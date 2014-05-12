/**
 * 
 */
package jenkins.plugins.play;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import jenkins.model.Jenkins;
import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;

/**
 * Abstract descriptor for each play command. Every command should provide a
 * list of Play versions it's compatible with.
 */
public abstract class PlayCommandDescriptor extends Descriptor<PlayCommand> {

	/**
	 * Version for which this command should be available. Matches the
	 * {@link PlayTarget} enum.
	 */
	protected List<PlayTarget> compatibleVersions = new ArrayList<PlayTarget>();

	/**
	 * @return List of compatible versions.
	 */
	public List<PlayTarget> compatibleVersions() {
		return compatibleVersions;
	}

	/**
	 * List of descriptor extension presented in the Jenkins interface according
	 * to the selected version.
	 * 
	 * @return List of descriptor extension filtered by version.
	 */
	public static DescriptorExtensionList<PlayCommand, PlayCommandDescriptor> all() {

		// Retrieve the complete list of descriptor extensions (one per play
		// command)
		DescriptorExtensionList<PlayCommand, PlayCommandDescriptor> list = Jenkins
				.getInstance().getDescriptorList(PlayCommand.class);

		// Iterate over commands to filter those compatible to the desired
		// version
		for (Iterator<PlayCommandDescriptor> iterator = list.iterator(); iterator
				.hasNext();) {

			PlayCommandDescriptor playExtensionDescriptor = iterator.next();

			// Remove from the list if the command isn't compatible with the
			// version
			if (!playExtensionDescriptor.compatibleVersions().contains(
					PlayTarget.PLAY_1_X))
				list.remove(playExtensionDescriptor);
		}

		return list;
	}

}