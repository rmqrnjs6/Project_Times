# Project_Times starter guide

Historical starter guide. The active project is now JSP/Java + Python + PostgreSQL; use the root README for current commands. The instructions below describe the earlier Python-only template and do not describe the current compose services.

## Local run

```sh
cp .env.example .env
docker compose up -d --build
```

Visit http://localhost:8000/ready. On Windows, use START_DOCKER.bat and STOP_DOCKER.bat. Docker must be installed and running.

## Tests

```sh
python -m pip install -r requirements-dev.txt
python -m pytest -q
```

## Reusing as a GitHub template

In GitHub repository Settings > General, enable **Template repository** (one-time owner/admin action). Then choose **Use this template** > **Create a new repository**. GitHub Actions will run on pushes and pull requests; successful status must be checked for each new project.

For scripting, authenticate GitHub CLI (`gh auth login`), then run:

```powershell
.\scripts\New-Project.ps1 -Name Project_Alpha
```

The script relies on the repository being marked as a template. New repositories are private by default. This is a scaffold, not a copy of all Project_Jiwoo application features.
