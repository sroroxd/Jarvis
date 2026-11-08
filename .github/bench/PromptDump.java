package de.art.jarvis;
public class PromptDump {
  public static void main(String[] a) throws Exception {
    java.nio.file.Files.writeString(java.nio.file.Path.of(a[0]), Persona.system(true, "", "", "", false, false));
    java.nio.file.Files.writeString(java.nio.file.Path.of(a[1]), Persona.turnContext(true, true, false, new java.util.Date()));
    java.nio.file.Files.writeString(java.nio.file.Path.of(a[2]), Persona.system(false, "", "", "", true, false));
    java.nio.file.Files.writeString(java.nio.file.Path.of(a[3]), Persona.turnContext(false, false, false, new java.util.Date()));
  }
}
