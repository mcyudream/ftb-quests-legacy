#version 120
uniform float iTime;
uniform vec3 iResolution;
uniform vec2 iOffset;
void main() {
    vec2 uv = (gl_FragCoord.xy-iOffset)/iResolution.xy;
    vec2 p = (uv-.5)*vec2(iResolution.x/iResolution.y,1.0);
    float pulse = .5+.5*sin(length(p)*16.0-iTime*.5+sin(p.x*8.0+iTime*.2));
    float grid = pow(max(abs(sin(p.x*30.0)),abs(sin(p.y*30.0))),24.0);
    vec3 tint = vec3(0.12,0.7,0.35);
    gl_FragColor = vec4(tint*(.06+.10*pulse+.08*grid),1.0);
}
