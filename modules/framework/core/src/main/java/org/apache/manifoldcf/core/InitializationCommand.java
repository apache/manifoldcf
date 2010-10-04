package org.apache.manifoldcf.core;

import org.apache.manifoldcf.core.interfaces.ManifoldCFException;

/**
 * Interface for commands that initialize state of the connector framework. Among implementations available are:
 * - Database creation
 * - Registrations of agent
 * - Registrations of connectors
 *
 * @author Jettro Coenradie
 */
public interface InitializationCommand
{
  /**
   * Execute the command.
   *
   * @throws ManifoldCFException Thrown if the execution fails
   */
  void execute() throws ManifoldCFException;
}
