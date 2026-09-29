package com.clinecan.backend.agent;

import com.clinecan.backend.model.GeneratedFile;
import org.springframework.stereotype.Component;
import java.util.List;

/** Deterministic starter generator. No LLM or generated-code execution. */
@Component
public class CodeGenerator {
    public List<GeneratedFile> generate(String prompt) {
        String safe = prompt.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("{", "&#123;").replace("}", "&#125;");
        return List.of(
            new GeneratedFile("src/App.tsx", """
                import './App.css'

                export default function App() {
                  return (
                    <main>
                      <span>BUILT WITH CLINECAN</span>
                      <h1>Clinecan Generated App</h1>
                      <p>%s</p>
                    </main>
                  )
                }
                """.formatted(safe)),
            new GeneratedFile("src/App.css", """
                :root { font-family: system-ui, sans-serif; color: #f5e6d3; background: #100b09; }
                body { margin: 0; }
                main { max-width: 760px; padding: 80px 24px; margin: auto; }
                span { color: #d58b55; font-size: 12px; letter-spacing: 3px; }
                h1 { font-size: clamp(32px, 6vw, 64px); }
                p { line-height: 1.7; overflow-wrap: anywhere; }
                """),
            new GeneratedFile("src/main.tsx", """
                import { StrictMode } from 'react'
                import { createRoot } from 'react-dom/client'
                import App from './App'
                createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>)
                """),
            new GeneratedFile("index.html", """
                <!doctype html>
                <html lang="en"><head><meta charset="UTF-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <title>Clinecan Generated App</title></head>
                <body><div id="root"></div><script type="module" src="/src/main.tsx"></script></body></html>
                """),
            new GeneratedFile("package.json", """
                {
                  "name": "clinecan-generated-app", "version": "0.0.1", "private": true, "type": "module",
                  "scripts": { "dev": "vite", "build": "tsc --noEmit && vite build" },
                  "dependencies": { "react": "^19.2.8", "react-dom": "^19.2.8" },
                  "devDependencies": { "@types/react": "^19.2.18", "@types/react-dom": "^19.2.7", "typescript": "~6.0.2", "vite": "^8.3.0" }
                }
                """),
            new GeneratedFile("tsconfig.json", """
                { "compilerOptions": { "target": "ES2023", "lib": ["ES2023", "DOM"],
                  "module": "ESNext", "moduleResolution": "bundler", "jsx": "react-jsx",
                  "types": ["vite/client"], "strict": true, "skipLibCheck": true, "noEmit": true }, "include": ["src"] }
                """),
            new GeneratedFile("vite.config.ts", "export default { server: { port: 5173 } }\n")
        );
    }
    public List<GeneratedFile> generate(com.clinecan.backend.model.ProjectSpecification specification) {
        String app = DemoApplications.app(specification);
        return generate(specification.description()).stream().map(file -> switch (file.path()) {
            case "src/App.tsx" -> new GeneratedFile(file.path(), app);
            case "src/App.css" -> new GeneratedFile(file.path(), DemoApplications.CSS);
            case "package.json" -> new GeneratedFile(file.path(), file.content().replace("clinecan-generated-app", specification.projectName()));
            default -> file;
        }).toList();
    }
}
