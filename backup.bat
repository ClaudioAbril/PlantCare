@echo off
copy *.* "E:\Archivos-Personales\GoogleDrive\Codigo Propio\HTML\PlantCare\"
copy *.* "E:\Archivos-Personales\OneDrive\Archivos Privados\Codigo Propio\HTML\PlantCare\"

echo.
echo ==================================================
echo    Subiendo cambios a GitHub...
echo ==================================================
echo.
git add index.html
git add GEMINI-plant_identifier_app.html
git add manifest.json
git add sw.js
git add backup.bat

git commit -m "Deploy automatico: Actualizacion de la app PlantCare"
REM Sube los cambios a la rama principal de tu repositorio en GitHub.
git push origin HEAD

echo.
echo ==================================================
echo      PROCESO DE PUBLICACION FINALIZADO
echo ==================================================
echo.

pause