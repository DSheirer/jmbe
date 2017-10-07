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

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class AntExecutor
{
    private final static Logger mLog = LoggerFactory.getLogger(AntExecutor.class);
    private Path mRepositoryPath;
    private Path mJavaHome;

    /**
     * Ant build script executor
     */
    public AntExecutor(Path repositoryPath, Path javaHome)
    {
        mRepositoryPath = repositoryPath;
        mJavaHome = javaHome;
    }

    public void build() throws BuildException
    {

        Project project = new Project();

        project.init();

        for(Map.Entry<String,Object> entry: project.getProperties().entrySet())
        {
            mLog.debug("Project Property: " + entry.getKey() + " = " + entry.getValue().toString());
        }

        project.addBuildListener(new DefaultLogger());

        Path buildScript = mRepositoryPath.resolve("jmbe/build/build.xml");

        if(Files.exists(buildScript))
        {
            mLog.info("Ant Build Script:" + buildScript.toString());
            mLog.info("Configuring Ant project with build script");
            ProjectHelper.configureProject(project, buildScript.toFile());

            Target compileTarget = project.getTargets().get("compile-library");

            if(compileTarget != null)
            {
                mLog.info("Compile target tasks:");
                for(Task task: compileTarget.getTasks())
                {
                    mLog.debug("Compile Task Name: " + task.getTaskName() + " " + task.getClass());
                }
            }
            else
            {
                mLog.info("Compile target not found!");
            }

            Object cp = project.getReference("classpath");

            if(cp instanceof org.apache.tools.ant.types.Path)
            {
                org.apache.tools.ant.types.Path cpPath = (org.apache.tools.ant.types.Path)cp;
                org.apache.tools.ant.types.Path.PathElement pathElement = cpPath.createPathElement();
                pathElement.setLocation(mJavaHome.resolve("lib").resolve("tools.jar").toFile());

                mLog.debug(cpPath.toString());
            }
            mLog.debug("CP is: " + cp.getClass());


            mLog.info("Project configured - building library");
            project.executeTarget("create-library");
        }
        else
        {
            mLog.error("JMBE Ant build file could not be found at: " + buildScript.toString());
            throw new BuildException("Could not find JMBE ant build file");
        }
    }
}
