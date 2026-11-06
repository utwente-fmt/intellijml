package nl.utwente.jmlplugin.annotator;

import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Holder for all the variables that are manually kept track of during type checking
 */
class ExtraVariables {
    private final Map<String, PsiType> vars = new HashMap<>();
    // the variables that are ghost variables, by name
    private final Map<String, Ghosts.Ghost> ghosts = new HashMap<>();

    public ExtraVariables() {

    }

    public ExtraVariables(ExtraVariables extraVars) {
        vars.putAll(extraVars.vars);
        ghosts.putAll(extraVars.ghosts);
    }

    /**
     * Adds a new variable to the holder, which hides a ghost variable with the same name
     *
     * @param name the name of the variable
     * @param type the type of the variable
     */
    public void add(@NotNull String name, @NotNull PsiType type) {
        vars.put(name, type);
        ghosts.remove(name);
    }

    /**
     * Adds a ghost variable to the holder
     *
     * @param ghost the ghost variable
     * @param type  its type
     */
    public void addGhost(@NotNull Ghosts.Ghost ghost, @NotNull PsiType type) {
        vars.put(ghost.name(), type);
        ghosts.put(ghost.name(), ghost);
    }

    /**
     * @param name the name of a variable
     * @return the ghost variable with that name, if the variable is one
     */
    public @Nullable Ghosts.Ghost findGhost(@NotNull String name) {
        return ghosts.get(name);
    }

    /**
     * Finds a variable by its name and returns the type of it
     *
     * @param name the name of the variable
     * @return the type of the found variable or null when the variable could not be found
     */
    public PsiType findVarAndGetType(@NotNull String name) {
        return vars.get(name);
    }

    /**
     * Checks that the holder contains a variable with a certain name
     *
     * @param name the name of the variable
     * @return true if the holder contains the variable
     */
    public boolean contains(@NotNull String name) {
        return vars.containsKey(name);
    }
}
