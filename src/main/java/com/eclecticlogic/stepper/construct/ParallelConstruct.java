package com.eclecticlogic.stepper.construct;

import com.eclecticlogic.stepper.StateMachine;
import com.eclecticlogic.stepper.antlr.StepperLexer;
import com.eclecticlogic.stepper.antlr.StepperParser;
import com.eclecticlogic.stepper.etc.WeaveContext;
import com.eclecticlogic.stepper.state.Parallel;
import com.eclecticlogic.stepper.visitor.StepperVisitor;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

public class ParallelConstruct extends StateConstruct<Parallel> {

    private List<String> references;

    public ParallelConstruct(Parallel state) {
        super(state);
    }


    public void setReferences(List<String> references) {
        this.references = references;
    }


    public void setResultPath(String path) {
        getState().setResultPath(path);
    }


    String getText(String reference) {
        if (reference.startsWith("stg://")) {
            String path = reference.substring(6);
            String[] parts = path.split("@");
            STGroup group = new STGroupFile(parts[1]);
            ST st = group.getInstanceOf(parts[0]);
            return st.render();
        } else {
            Path path;
            if (reference.startsWith("classpath://")) {
                path = Paths.get(getClass().getClassLoader()
                        .getResource(reference.substring(12)).getFile());
            } else {
                path = Paths.get(reference);
            }
            try (Stream<String> lines = Files.lines(path)) {
                String data = lines.collect(joining("\n"));
                return data;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }


    @Override
    public void weave(WeaveContext context) {
        Parallel state = getState();
        state.captureAttribute("Branches");
        state.handleArray(() -> {
            for (String reference : references) {
                String text = getText(reference);

                CharStream input = CharStreams.fromString(text);
                StepperLexer lexer = new StepperLexer(input);
                CommonTokenStream tokens = new CommonTokenStream(lexer);
                StepperParser parser = new StepperParser(tokens);

                StepperVisitor visitor = new StepperVisitor(context);
                visitor.setSuppressAnnotations(true);
                StateMachine machine = visitor.visitProgram(parser.program());
                state.setObject(machine.toJson());
            }
        });
        super.weave(context);
    }


}