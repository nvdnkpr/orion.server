/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.jobs;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.objects.Log;
import org.eclipse.orion.server.git.objects.Tag;
import org.eclipse.orion.server.git.servlets.GitUtils;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * This job handles generating tag list
 *
 */
public class ListTagsJob extends GitJob {

	private IPath path;
	private URI cloneLocation;
	private int commitsSize;
	private int pageNo;
	private int pageSize;
	private String baseLocation;

	/**
	 * Creates job with given page range and adding <code>commitsSize</code> commits to every tag.
	 * @param userRunningTask
	 * @param repositoryPath
	 * @param cloneLocation
	 * @param commitsSize user 0 to omit adding any log, only CommitLocation will be attached 
	 * @param pageNo
	 * @param pageSize use negative to indicate that all commits need to be returned
	 * @param baseLocation URI used as a base for generating next and previous page links. Should not contain any parameters.
	 */
	public ListTagsJob(String userRunningTask, IPath repositoryPath, URI cloneLocation, int commitsSize, int pageNo, int pageSize, String baseLocation) {
		super(NLS.bind("Generating tags list for {0}", repositoryPath), userRunningTask, NLS.bind("Generating tags list for {0}...", repositoryPath), true, false);
		this.path = repositoryPath;
		this.cloneLocation = cloneLocation;
		this.commitsSize = commitsSize;
		this.pageNo = pageNo;
		this.pageSize = pageSize;
		this.baseLocation = baseLocation;
		setFinalMessage("Generating tags list completed");
	}

	/**
	 * Creates job returning list of all tags adding <code>commitsSize</code> commits to every tag.
	 * @param userRunningTask
	 * @param repositoryPath
	 * @param cloneLocation
	 * @param commitsSize
	 */
	public ListTagsJob(String userRunningTask, IPath repositoryPath, URI cloneLocation, int commitsSize) {
		this(userRunningTask, repositoryPath, cloneLocation, commitsSize, 1, -1, null);
	}

	/**
	 * Creates job returning list of all tags.
	 * @param userRunningTask
	 * @param repositoryPath
	 * @param cloneLocation
	 */
	public ListTagsJob(String userRunningTask, IPath repositoryPath, URI cloneLocation) {
		this(userRunningTask, repositoryPath, cloneLocation, 0);
	}

	private ObjectId getCommitObjectId(Repository db, ObjectId oid) throws MissingObjectException, IncorrectObjectTypeException, IOException {
		RevWalk walk = new RevWalk(db);
		try {
			return walk.parseCommit(oid);
		} finally {
			walk.release();
		}
	}

	@Override
	protected IStatus performJob() {
		try {
			// list all tags
			File gitDir = GitUtils.getGitDir(path);
			Repository db = new FileRepository(gitDir);
			Git git = new Git(db);
			List<Ref> refs = git.tagList().call();
			JSONObject result = new JSONObject();
			List<Tag> tags = new ArrayList<Tag>();
			for (Ref ref : refs) {
				Tag tag = new Tag(cloneLocation, db, ref);
				tags.add(tag);
			}
			Collections.sort(tags, Tag.COMPARATOR);
			JSONArray children = new JSONArray();
			int firstTag = pageSize > 0 ? pageSize * (pageNo - 1) : 0;
			int lastTag = pageSize > 0 ? firstTag + pageSize - 1 : tags.size() - 1;
			lastTag = lastTag > tags.size() - 1 ? tags.size() - 1 : lastTag;
			if (pageNo > 1 && baseLocation != null) {
				String prev = baseLocation + "?page=" + (pageNo - 1) + "&pageSize=" + pageSize;
				if (commitsSize > 0) {
					prev += "&" + GitConstants.KEY_TAG_COMMITS + "=" + commitsSize;
				}
				result.put(ProtocolConstants.KEY_PREVIOUS_LOCATION, prev);
			}
			if (lastTag < tags.size() - 1) {
				String next = baseLocation + "?page=" + (pageNo + 1) + "&pageSize=" + pageSize;
				if (commitsSize > 0) {
					next += "&" + GitConstants.KEY_TAG_COMMITS + "=" + commitsSize;
				}
				result.put(ProtocolConstants.KEY_NEXT_LOCATION, next);
			}
			for (int i = firstTag; i <= lastTag; i++) {
				Tag tag = tags.get(i);
				if (this.commitsSize == 0) {
					children.put(tag.toJSON());
				} else {
					// add info about commits if requested
					LogCommand lc = git.log();
					String toCommitName = tag.getRevCommitName();
					ObjectId toCommitId = db.resolve(toCommitName);
					Ref toCommitRef = db.getRef(toCommitName);
					toCommitId = getCommitObjectId(db, toCommitId);
					lc.add(toCommitId);
					lc.setMaxCount(this.commitsSize);
					Iterable<RevCommit> commits = lc.call();
					Log log = new Log(cloneLocation, db, commits, null, null, toCommitRef);
					log.setPaging(1, commitsSize);
					children.put(tag.toJSON(log.toJSON()));
				}
			}
			result.put(ProtocolConstants.KEY_CHILDREN, children);
			result.put(ProtocolConstants.KEY_TYPE, Tag.TYPE);
			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result);
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when listing tags for {0}", path);
			return new Status(IStatus.ERROR, GitActivator.PI_GIT, msg, e);
		}
	}
}
