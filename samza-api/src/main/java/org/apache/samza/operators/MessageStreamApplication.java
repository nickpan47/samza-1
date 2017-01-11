package org.apache.samza.operators;

import org.apache.samza.config.Config;
import org.apache.samza.system.ExecutionEnvironment;


/**
 * Created by yipan on 1/5/17.
 */
public abstract class MessageStreamApplication {
  public final void run(ExecutionEnvironment env, Config config) {
    try {
      MessageStreamGraph graph = env.createGraph(config);
      initGraph(graph, config);
      env.run(graph);
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  public abstract void initGraph(MessageStreamGraph graph, Config config);

  public static MessageStreamApplication fromConfig(Config config) {
    // TODO: placeholder. Should load the class name from config and instantiate the application instance
    // TODO: add config var example that set the user-implemented {@link MessageStreamApplication} class
    return null;
  }
}
