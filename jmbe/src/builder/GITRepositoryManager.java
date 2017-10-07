/*
 * **********************************************************
 * jmbe - Java MBE Library
 * Copyright (C) 2014-2017 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * **********************************************************
 */

package builder;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

public class GITRepositoryManager
{
    private final static Logger mLog = LoggerFactory.getLogger(GITRepositoryManager.class);

    private static final String REPOSITORY_URI = "https://github.com/DSheirer/jmbe.git";

    private Path mRepositoryDirectory;
    private Git mGit;
    private Ref mMasterBranchReference;
    private Repository mRepository;

    /**
     * Repository manager for JMBE source code repository on GitHub
     *
     * @param repositoryPath file system path where the temporary GIT repository is located or where it
     * will be created.
     */
    public GITRepositoryManager(Path repositoryPath)
    {
        mRepositoryDirectory = repositoryPath;
    }

    public GITRepositoryManager()
    {
    }

    public Path getRepositoryDirectory()
    {
        return mRepositoryDirectory;
    }

    public void download() throws IOException
    {

    }

    public void init() throws IOException, GitAPIException
    {
        if(mMasterBranchReference == null)
        {
            mLog.info("Initializing GIT repository");

            if(mRepositoryDirectory == null)
            {
                mRepositoryDirectory = getHomePath();
                mLog.info("Local Temporary Workspace: " + mRepositoryDirectory.toString());
            }

            if(!Files.exists(mRepositoryDirectory))
            {
                Files.createDirectory(mRepositoryDirectory);
            }

            Path repositoryPath = mRepositoryDirectory.resolve(".git");

            mLog.info("Local GIT Repository: " + repositoryPath.toString());

            if(Files.exists(repositoryPath))
            {
                mLog.info("Opening existing GIT Repository");
                mRepository = FileRepositoryBuilder.create(repositoryPath.toFile());
                mGit = new Git(mRepository);

                mLog.info("Pulling latest changes from master branch ..");
                PullResult pullResult = mGit.pull().call();
                if(pullResult.getMergeResult().getMergeStatus().isSuccessful())
                {
                    mLog.info("Pull complete - successful merge");
                }
                else
                {
                    mLog.info("Pull complete - merge UNSUCCESSFUL - conflicts: " + pullResult.getMergeResult().getCheckoutConflicts());
                }
            }
            else
            {
                mLog.info("Creating new local GIT Repository");
                mRepository = new FileRepositoryBuilder()
                    .setGitDir(mRepositoryDirectory.toFile())
                    .build();

                mLog.info("Cloning: " + REPOSITORY_URI);
                mGit = Git
                    .cloneRepository()
                    .setURI(REPOSITORY_URI)
                    .setDirectory(mRepositoryDirectory.toFile())
                    .call();
            }

        }
    }

    /**
     * Gets (or creates) a temporary directory for JMBE to use in the user's home directory.
     */
    private static Path getHomePath()
    {
        Path homePath = FileSystems.getDefault().getPath(System.getProperty("user.home"), "jmbe_builder/source_code");

        if(!Files.exists(homePath))
        {
            try
            {
                Files.createDirectory(homePath);

                mLog.info("JMBE - created temporary directory [" + homePath.toString() + "]");
            }
            catch(Exception e)
            {
                homePath = null;

                mLog.error("JMBE: exception while creating temporary directory in the user's home directory", e);
            }
        }

        return homePath;
    }
}
