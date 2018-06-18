package de.codersourcery.m68k.assembler.phases;

import de.codersourcery.m68k.assembler.ICompilationContext;
import de.codersourcery.m68k.assembler.ICompilationPhase;
import de.codersourcery.m68k.parser.Lexer;
import de.codersourcery.m68k.parser.Parser;
import de.codersourcery.m68k.parser.StringScanner;
import de.codersourcery.m68k.parser.ast.AST;

public class ParsePhase implements ICompilationPhase
{
    @Override
    public void run(ICompilationContext ctx) throws Exception
    {
        final Parser p = new Parser();
        final String source = ctx.getSource(ctx.getCompilationUnit());
        final AST ast = p.parse(new Lexer(new StringScanner(source)));
        System.out.println("AST: \n"+ast);
        ctx.getCompilationUnit().setAST(ast);
    }

    @Override
    public String toString()
    {
        return "Parse phase";
    }
}
